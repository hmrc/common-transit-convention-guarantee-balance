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
import v2.models.errors.PresentationError

sealed abstract class AuditEvent extends Product with Serializable

case class BalanceRequestSucceededEvent(
  userInternalId: InternalId,
  guaranteeReference: GuaranteeReferenceNumber,
  accessCode: AccessCode
) extends AuditEvent

case object BalanceRequestSucceededEvent {

  implicit val balanceRequestSucceededEventWrites: OWrites[BalanceRequestSucceededEvent] =
    Json.writes[BalanceRequestSucceededEvent]

}

case class GRNNotFoundEvent(
  userInternalId: InternalId,
  guaranteeReference: GuaranteeReferenceNumber,
  accessCode: AccessCode,
  reason: String
) extends AuditEvent

case object GRNNotFoundEvent {

  implicit val guaranteeNumberNotFoundEventWrites: OWrites[GRNNotFoundEvent] =
    Json.writes[GRNNotFoundEvent]
}

case class RateLimitedEvent(
  userInternalId: InternalId,
  guaranteeReference: GuaranteeReferenceNumber,
  accessCode: AccessCode,
  reason: String
) extends AuditEvent

case object RateLimitedEvent {

  implicit val rateLimitedEventWrites: OWrites[RateLimitedEvent] =
    Json.writes[RateLimitedEvent]
}

case class BalanceRequestFailedEvent(
  internalId: InternalId,
  body: JsValue,
  grn: GuaranteeReferenceNumber,
  reason: PresentationError
) extends AuditEvent

case object BalanceRequestFailedEvent {

  implicit val balanceRequestFailedEventWrites: OWrites[BalanceRequestFailedEvent] =
    Json.writes[BalanceRequestFailedEvent]
}
