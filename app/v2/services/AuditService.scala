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

package v2.services

import cats.data.NonEmptyList
import com.google.inject.ImplementedBy
import models.request.AuthenticatedRequest
import play.api.libs.json.JsValue
import runtime.IOFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import v2.models.AuditEventType
import v2.models.AuditInfo
import v2.models.Balance
import v2.models.ErrorResponseEvent
import v2.models.GuaranteeReferenceNumber
import v2.models.InvalidPayloadEvent
import v2.models.RateLimitedRequestEvent
import v2.models.RequestSentEvent
import v2.models.errors.RequestLockingError
import v2.models.errors.RoutingError
import v2.models.errors.ValidationError

import javax.inject._
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {

  def balanceRequestSucceeded(auditInfo: AuditInfo, balance: Balance)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit

  def balanceRequestFailed[E](error: E)(implicit
    auditInfo: AuditInfo,
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit

  def invalidPayloadBalanceRequest(request: AuthenticatedRequest[JsValue], grn: GuaranteeReferenceNumber)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit
}

@Singleton
class AuditServiceImpl @Inject() (connector: AuditConnector) extends AuditService with IOFutures {

  override def balanceRequestSucceeded(auditInfo: AuditInfo, balance: Balance)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    connector.sendExplicitAudit[RequestSentEvent](
      AuditEventType.RequestSent.name,
      RequestSentEvent(
        auditInfo.internalId,
        auditInfo.guaranteeReferenceNumber,
        auditInfo.balanceRequest.accessCode,
        balance
      )
    )

  private def grnNotFound()(implicit
    auditInfo: AuditInfo,
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    connector.sendExplicitAudit[ErrorResponseEvent](
      AuditEventType.ErrorResponse.name,
      ErrorResponseEvent.toEvent(auditInfo, "Guarantee Reference Number NotFound")
    )

  private def rateLimitExceeded()(implicit
    auditInfo: AuditInfo,
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    connector.sendExplicitAudit[RateLimitedRequestEvent](
      AuditEventType.RateLimitedRequest.name,
      RateLimitedRequestEvent.toEvent
    )

  private def invalidAccessCode()(implicit
    auditInfo: AuditInfo,
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    connector.sendExplicitAudit[ErrorResponseEvent](
      AuditEventType.ErrorResponse.name,
      ErrorResponseEvent.toEvent(auditInfo, "Invalid Access Code")
    )

  private def serverError()(implicit
    auditInfo: AuditInfo,
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    connector.sendExplicitAudit[ErrorResponseEvent](
      AuditEventType.ErrorResponse.name,
      ErrorResponseEvent.toEvent(auditInfo, "Server Error")
    )

  override def invalidPayloadBalanceRequest(request: AuthenticatedRequest[JsValue], grn: GuaranteeReferenceNumber)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit =
    connector.sendExplicitAudit[InvalidPayloadEvent](
      AuditEventType.InvalidPayload.name,
      InvalidPayloadEvent(request.internalId, grn, request.body)
    )

  override def balanceRequestFailed[E](originalError: E)(implicit
    auditInfo: AuditInfo,
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit = originalError match {
    case RoutingError.InvalidAccessCode                                 => invalidAccessCode()
    case RoutingError.GuaranteeReferenceNotFound                        => grnNotFound()
    case RoutingError.Unexpected(_) | RequestLockingError.Unexpected(_) => serverError()
    case RequestLockingError.AlreadyLocked                              => rateLimitExceeded()
    case e: NonEmptyList[ValidationError]                               => invalidAccessCode()
    case _                                                              => serverError()
  }

}
