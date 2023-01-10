/*
 * Copyright 2023 HM Revenue & Customs
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

package v2.controllers

import cats.data.EitherT
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.google.inject.Inject
import com.kenshoo.play.metrics.Metrics
import controllers.ErrorLogging
import controllers.actions.AuthActionProvider
import controllers.actions.IOActions
import logging.Logging
import metrics.IOMetrics
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import v2.models.BalanceRequest
import v2.models.GuaranteeReferenceNumber
import v2.models.HateoasResponse
import v2.models.errors.PresentationError
import v2.services.RequestLockingService
import v2.services.RouterService
import v2.services.ValidationService

class GuaranteeBalanceController @Inject() (
  authenticate: AuthActionProvider,
  lockService: RequestLockingService,
  validationService: ValidationService,
  routerService: RouterService,
  cc: ControllerComponents,
  val runtime: IORuntime,
  val metrics: Metrics
) extends BackendController(cc)
    with IOActions
    with IOMetrics
    with Logging
    with ErrorLogging
    with ErrorTranslator {

  private val AcceptHeaderRegex = """application/vnd\.hmrc\.(2.0)\+json""".r

  def postRequest(grn: GuaranteeReferenceNumber): Action[JsValue] =
    authenticate().io(parse.json) {
      implicit request =>
        (for {
          _                <- validateAcceptHeader
          _                <- lockService.lock(grn, request.internalId).asPresentation
          parsed           <- parseJson(request.body)
          _                <- validationService.validate(parsed).asPresentation
          internalResponse <- routerService.request(grn, parsed).asPresentation
          hateoas          <- EitherT.right[PresentationError](HateoasResponse(grn, internalResponse))
        } yield hateoas).fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          result => Ok(result)
        )
    }

  private def parseJson(json: JsValue): EitherT[IO, PresentationError, BalanceRequest] =
    EitherT {
      IO {
        json.validate[BalanceRequest].asEither match {
          case Right(x) => Right(x)
          case Left(_)  => Left(PresentationError.badRequestError("The access code was not supplied."))
        }
      }
    }

  private def validateAcceptHeader(implicit request: Request[_]): EitherT[IO, PresentationError, Unit] =
    EitherT {
      IO {
        request.headers.get(ACCEPT) match {
          case Some(AcceptHeaderRegex(_)) => Right(())
          case _ =>
            Left(PresentationError.notAcceptableError("The accept header must be set to application/vnd.hmrc.2.0+json to use this resource."))
        }
      }
    }

}