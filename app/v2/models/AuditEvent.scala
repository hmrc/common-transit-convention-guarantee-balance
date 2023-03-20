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

package v2.models

import models.values.InternalId
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OWrites

sealed abstract class AuditEvent extends Product with Serializable

case class RequestSentEvent(
  userInternalId: InternalId,
  guaranteeReference: GuaranteeReferenceNumber,
  accessCode: AccessCode,
  balance: Balance
) extends AuditEvent

case object RequestSentEvent {

  implicit val successResponseEventWrites: OWrites[RequestSentEvent] =
    Json.writes[RequestSentEvent]

}

case class InvalidPayloadEvent(
  userInternalId: InternalId,
  guaranteeReference: GuaranteeReferenceNumber,
  payload: JsValue
) extends AuditEvent

case object InvalidPayloadEvent {

  implicit val invalidPayloadEventWrites: OWrites[InvalidPayloadEvent] =
    Json.writes[InvalidPayloadEvent]
}

case class RateLimitedRequestEvent(
  userInternalId: InternalId,
  guaranteeReference: GuaranteeReferenceNumber,
  accessCode: AccessCode
) extends AuditEvent

case object RateLimitedRequestEvent {

  implicit val rateLimitedRequestEventWrites: OWrites[RateLimitedRequestEvent] =
    Json.writes[RateLimitedRequestEvent]

  def toEvent(implicit auditInfo: AuditInfo): RateLimitedRequestEvent =
    RateLimitedRequestEvent(
      auditInfo.internalId,
      auditInfo.guaranteeReferenceNumber,
      auditInfo.balanceRequest.accessCode
    )
}

case class ErrorResponseEvent(
  userInternalId: InternalId,
  guaranteeReference: GuaranteeReferenceNumber,
  accessCode: AccessCode,
  errorType: String
) extends AuditEvent

case object ErrorResponseEvent {

  implicit val errorResponseEventWrites: OWrites[ErrorResponseEvent] =
    Json.writes[ErrorResponseEvent]

  def toEvent(implicit auditInfo: AuditInfo, errorDetails: String): ErrorResponseEvent =
    ErrorResponseEvent(
      auditInfo.internalId,
      auditInfo.guaranteeReferenceNumber,
      auditInfo.balanceRequest.accessCode,
      errorDetails
    )
}
