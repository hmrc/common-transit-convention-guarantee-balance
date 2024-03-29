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

import config.Constants.InvalidGRNCode
import config.Constants.InvalidGRNFormat
import play.api.Configuration
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.http.JsonErrorHandler
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent
import v2.models.errors.PresentationError

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CustomJsonErrorHandler @Inject() (
  auditConnector: AuditConnector,
  httpAuditEvent: HttpAuditEvent,
  configuration: Configuration
)(implicit ec: ExecutionContext)
    extends JsonErrorHandler(auditConnector, httpAuditEvent, configuration)(ec) {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    if (statusCode == BAD_REQUEST) {
      super.onClientError(request, statusCode, message).map {
        error =>
          message match {
            case InvalidGRNCode =>
              BadRequest(Json.toJson(PresentationError.badRequestError("The guarantee reference number must be for a GB or XI guarantee.")))
            case InvalidGRNFormat =>
              BadRequest(Json.toJson(PresentationError.badRequestError("The guarantee reference number is not in the correct format.")))
            case _ => error
          }
      }
    } else {
      super.onClientError(request, statusCode, message)
    }

  override def onServerError(request: RequestHeader, ex: Throwable): Future[Result] =
    super.onServerError(request, ex)
}
