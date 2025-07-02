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
import com.google.inject.Inject
import controllers.actions.AuthActionProvider
import models.request.AuthenticatedRequest
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import v2.models.AuditInfo
import v2.models.BalanceRequest
import v2.models.GuaranteeReferenceNumber
import v2.models.HateoasResponse
import v2.models.errors.PresentationError
import v2.services.AuditService
import v2.services.RequestLockingService
import v2.services.RouterService
import v2.services.ValidationService

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class GuaranteeBalanceController @Inject() (
  authenticate: AuthActionProvider,
  lockService: RequestLockingService,
  validationService: ValidationService,
  routerService: RouterService,
  auditService: AuditService,
  cc: ControllerComponents,
  val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with ErrorTranslator {

  private val AcceptHeaderRegex = """application/vnd\.hmrc\.(2.0)\+json""".r

  def postRequest(grn: GuaranteeReferenceNumber): Action[JsValue] =
    authenticate().async(parse.json) {
      implicit request =>
        (for {
          _      <- validateAcceptHeader(grn)
          parsed <- parseJson(request, grn)
          auditInfo = AuditInfo(parsed, grn, request.internalId)
          _                <- lockService.lock(grn, request.internalId).asPresentation(auditInfo, auditService)
          _                <- validationService.validate(parsed).asPresentation(auditInfo, auditService)
          internalResponse <- routerService.request(grn, parsed).asPresentation(auditInfo, auditService)
          hateoas = HateoasResponse(grn, internalResponse)
          _       = auditService.balanceRequestSucceeded(auditInfo, internalResponse.balance)
        } yield hateoas).fold(
          presentationError => Status(presentationError.code.statusCode)(Json.toJson(presentationError)),
          result => Ok(result)
        )
    }

  private def parseJson(request: AuthenticatedRequest[JsValue], guaranteeReferenceNumber: GuaranteeReferenceNumber)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, PresentationError, BalanceRequest] =
    EitherT {
      Future {
        request.body.validate[BalanceRequest].asEither match {
          case Right(x) => Right(x)
          case Left(_) =>
            auditService.invalidPayloadBalanceRequest(request, guaranteeReferenceNumber)
            Left(PresentationError.badRequestError("The access code was not supplied."))
        }
      }
    }

  private def validateAcceptHeader(guaranteeReferenceNumber: GuaranteeReferenceNumber)(implicit
    request: AuthenticatedRequest[JsValue],
    hc: HeaderCarrier
  ): EitherT[Future, PresentationError, Unit] =
    EitherT {
      Future {
        request.headers.get(ACCEPT) match {
          case Some(AcceptHeaderRegex(_)) => Right(())
          case _ =>
            auditService.invalidPayloadBalanceRequest(request, guaranteeReferenceNumber)
            Left(PresentationError.notAcceptableError("The accept header must be set to application/vnd.hmrc.2.0+json to use this resource."))
        }
      }
    }
}
