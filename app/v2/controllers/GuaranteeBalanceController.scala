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

import cats.syntax.either.*
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

  def postRequest(grn: GuaranteeReferenceNumber): Action[JsValue] =
    authenticate().async(parse.json) {
      implicit request =>
        (for {
          _      <- validationService.validateAcceptHeader(grn).toEitherT[Future]
          parsed <- parseJson(request, grn).toEitherT[Future]
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

  private def parseJson(request: AuthenticatedRequest[JsValue], grn: GuaranteeReferenceNumber)(implicit
    hc: HeaderCarrier
  ): Either[PresentationError, BalanceRequest] =
    request.body
      .validate[BalanceRequest]
      .asEither
      .leftMap {
        _ =>
          auditService.invalidPayloadBalanceRequest(request, grn)
          PresentationError.badRequestError("The access code was not supplied.")
      }
}
