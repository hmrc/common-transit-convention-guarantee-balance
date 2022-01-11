/*
 * Copyright 2022 HM Revenue & Customs
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
import models.backend.BalanceRequestXmlError
import models.backend.PendingBalanceRequest
import models.backend.errors.FunctionalError
import models.errors.BadRequestError
import models.errors.BalanceRequestError
import models.errors.InternalServiceError
import models.errors.JsonParsingError
import models.errors.MissingAcceptHeaderError
import models.errors.MultipleErrors
import models.errors.NotFoundError
import models.errors.TooManyRequestsError
import models.errors.UpstreamTimeoutError
import models.request.BalanceRequest
import models.response._
import models.values.BalanceId
import models.values.ErrorPointer
import models.values.ErrorType
import play.api.http.HeaderNames
import play.api.i18n.I18nSupport
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import play.api.mvc.Result
import services.AuditService
import services.BalanceRequestLockService
import services.BalanceRequestService
import services.BalanceRequestValidationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import javax.inject.Singleton
import scala.util.control.NonFatal

@Singleton
class BalanceRequestController @Inject() (
  appConfig: AppConfig,
  authenticate: AuthActionProvider,
  service: BalanceRequestService,
  lockService: BalanceRequestLockService,
  auditService: AuditService,
  validator: BalanceRequestValidationService,
  cc: ControllerComponents,
  val runtime: IORuntime,
  val metrics: Metrics
) extends BackendController(cc)
  with IOActions
  with IOMetrics
  with I18nSupport
  with Logging
  with ErrorLogging
  with LocationWithContext {

  import MetricsKeys.Controllers._

  private val AcceptHeaderRegex = """application/vnd\.hmrc\.(1.0)\+json""".r

  private def requireAcceptHeader[A](
    continue: => IO[Result]
  )(implicit request: Request[A]): IO[Result] =
    request.headers
      .get(HeaderNames.ACCEPT)
      .flatMap(AcceptHeaderRegex.findFirstIn)
      .map(_ => continue)
      .getOrElse {
        val error     = MissingAcceptHeaderError()
        val errorJson = Json.toJson(error)
        IO.pure(NotAcceptable(errorJson))
      }

  /** GMS has a configurable query limit (between 1 and 1000 requests in 24h per requester EORI).
    *
    * When the limit is reached for an EORI GMS returns an error of type 26, which usually indicates
    * a duplicate message ID, but with the error pointer pointing at the requester EORI field.
    */
  private def hasQueryLimitError(
    error: BalanceRequestFunctionalError
  ): Boolean = {
    error.errors.collectFirst {
      case functionalError @ FunctionalError(
            ErrorType.DuplicateDetected,
            ErrorPointer.RequesterEori,
            _
          ) =>
        functionalError
    }.nonEmpty
  }

  private def validateRequest(
    continue: BalanceRequest => IO[Result]
  )(implicit request: Request[JsValue]): IO[Result] =
    IO(request.body.validate[BalanceRequest]).flatMap {
      case JsError(errors) =>
        val error     = JsonParsingError(errors = errors)
        val errorJson = Json.toJson(error)
        IO.pure(BadRequest(errorJson))
      case JsSuccess(balanceRequest, _) =>
        validator
          .validate(balanceRequest)
          .map(continue)
          .valueOr { errors =>
            val error     = MultipleErrors(errors = errors)
            val errorJson = Json.toJson[BadRequestError](error)
            IO.pure(BadRequest(errorJson))
          }
    }

  def submitBalanceRequest: Action[JsValue] =
    authenticate().io(parse.json) { implicit request =>
      withMetricsTimerResult(SubmitBalanceRequest) {
        requireAcceptHeader {
          validateRequest { balanceRequest =>
            lockService
              .isLockedOut(balanceRequest.guaranteeReference, request.internalId)
              .ifM(
                ifTrue = for {
                  result <- IO(TooManyRequests(Json.toJson(TooManyRequestsError())))
                  _      <- auditService.auditRateLimitedRequest(request, balanceRequest)
                } yield result,
                ifFalse = {
                  service
                    .submitBalanceRequest(balanceRequest)
                    .flatTap(logServiceError("submitting balance request", _))
                    .map {
                      case Right(Right(success @ BalanceRequestSuccess(_, _))) =>
                        Ok(Json.toJson(PostBalanceRequestSuccessResponse(success)))

                      case Right(Right(error @ BalanceRequestFunctionalError(_)))
                          if hasQueryLimitError(error) =>
                        TooManyRequests(Json.toJson(TooManyRequestsError()))

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
                          Accepted(Json.toJson(PostBalanceRequestPendingResponse(balanceId)))
                            .withHeaders(
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
              )
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
