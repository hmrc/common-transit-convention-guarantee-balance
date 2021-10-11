/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import controllers.actions.AuthActionProvider
import controllers.actions.IOActions
import logging.Logging
import metrics.IOMetrics
import metrics.MetricsKeys
import models.backend.BalanceRequestFunctionalError
import models.backend.BalanceRequestSuccess
import models.errors.BalanceRequestError
import models.errors.InternalServiceError
import models.errors.MissingAcceptHeaderError
import models.errors.NotFoundError
import models.errors.UpstreamTimeoutError
import models.request.BalanceRequest
import models.response._
import models.values.BalanceId
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import play.api.mvc.Result
import services.BalanceRequestService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import javax.inject.Singleton
import scala.util.control.NonFatal
import models.backend.PendingBalanceRequest
import models.backend.BalanceRequestXmlError

@Singleton
class BalanceRequestController @Inject() (
  appConfig: AppConfig,
  authenticate: AuthActionProvider,
  service: BalanceRequestService,
  cc: ControllerComponents,
  val runtime: IORuntime,
  val metrics: Metrics
) extends BackendController(cc)
  with IOActions
  with IOMetrics
  with Logging
  with ErrorLogging
  with LocationWithContext {

  import MetricsKeys.Controllers._

  private val AcceptHeaderRegex = """application/vnd\.hmrc\.(1.0)\+json""".r

  private def requireAcceptHeader[A](
    result: => IO[Result]
  )(implicit request: Request[A]): IO[Result] =
    request.headers
      .get(HeaderNames.ACCEPT)
      .flatMap(AcceptHeaderRegex.findFirstIn)
      .map(_ => result)
      .getOrElse {
        val error     = MissingAcceptHeaderError()
        val errorJson = Json.toJson(error)
        IO.pure(NotAcceptable(errorJson))
      }

  def submitBalanceRequest: Action[BalanceRequest] =
    authenticate().io(parse.json[BalanceRequest]) { implicit request =>
      withMetricsTimerResult(SubmitBalanceRequest) {
        requireAcceptHeader {
          service
            .submitBalanceRequest(request.body)
            .flatTap(logServiceError("submitting balance request", _))
            .map {
              case Right(Right(success @ BalanceRequestSuccess(_, _))) =>
                Ok(Json.toJson(PostBalanceRequestSuccessResponse(success)))

              case Right(Right(error @ BalanceRequestFunctionalError(_))) =>
                BadRequest(Json.toJson(PostBalanceRequestFunctionalErrorResponse(error)))

              case Right(Right(BalanceRequestXmlError(_))) =>
                InternalServerError(Json.toJson(BalanceRequestError.internalServiceError()))

              case Right(Left(balanceId)) =>
                if (!appConfig.asyncBalanceResponse) {
                  val error     = UpstreamTimeoutError()
                  val errorJson = Json.toJson[BalanceRequestError](error)
                  GatewayTimeout(errorJson)
                } else {
                  Accepted(Json.toJson(PostBalanceRequestPendingResponse(balanceId))).withHeaders(
                    HeaderNames.LOCATION -> routes.BalanceRequestController
                      .getBalanceRequest(balanceId)
                      .pathWithContext
                  )
                }

              case Left(error @ UpstreamTimeoutError(_)) =>
                GatewayTimeout(Json.toJson[BalanceRequestError](error))

              case Left(error) =>
                InternalServerError(Json.toJson[BalanceRequestError](error))
            }
            .recoverWith { case NonFatal(e) =>
              logger.error(e)("Unhandled exception thrown").map { _ =>
                val error     = InternalServiceError.causedBy(e)
                val errorJson = Json.toJson[BalanceRequestError](error)
                InternalServerError(errorJson)
              }
            }
        }
      }
    }

  def getBalanceRequest(balanceId: BalanceId): Action[AnyContent] =
    authenticate().io { implicit request =>
      withMetricsTimerResult(GetBalanceRequest) {
        requireAcceptHeader {
          service
            .getBalanceRequest(balanceId)
            .flatTap(logServiceError("fetching balance request", _))
            .map {
              case Right(PendingBalanceRequest(_, _, _, _, _, Some(BalanceRequestXmlError(_)))) =>
                InternalServerError(Json.toJson(BalanceRequestError.internalServiceError()))

              case Right(pendingRequest) =>
                Ok(Json.toJson(GetBalanceRequestResponse(balanceId, pendingRequest)))

              case Left(error @ NotFoundError(_)) =>
                NotFound(Json.toJson[BalanceRequestError](error))

              case Left(error) =>
                InternalServerError(Json.toJson[BalanceRequestError](error))
            }
            .recoverWith { case NonFatal(e) =>
              logger.error(e)("Unhandled exception thrown").map { _ =>
                val error     = InternalServiceError.causedBy(e)
                val errorJson = Json.toJson[BalanceRequestError](error)
                InternalServerError(errorJson)
              }
            }
        }
      }
    }
}
