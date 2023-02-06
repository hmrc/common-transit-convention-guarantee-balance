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
import cats.effect.IO
import com.google.inject.ImplementedBy
import models.request.AuthenticatedRequest
import play.api.libs.json.JsValue
import runtime.IOFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import v2.models.AccessCodeNotValidEvent
import v2.models.AuditEventType
import v2.models.AuditInfo
import v2.models.Balance
import v2.models.BalanceRequestSucceededEvent
import v2.models.GRNNotFoundEvent
import v2.models.GuaranteeReferenceNumber
import v2.models.InvalidPayloadEvent
import v2.models.RateLimitedEvent
import v2.models.ServerErrorEvent
import v2.models.errors.RequestLockingError
import v2.models.errors.RoutingError
import v2.models.errors.ValidationError

import javax.inject._

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {

  def balanceRequestSucceeded(auditInfo: AuditInfo, balance: Balance)(implicit
    hc: HeaderCarrier
  ): IO[Unit]

  def balanceRequestFailed[E](error: E)(implicit
    auditInfo: AuditInfo,
    hc: HeaderCarrier
  ): IO[Unit]

  def balanceRequestFailed(request: AuthenticatedRequest[JsValue], grn: GuaranteeReferenceNumber)(implicit hc: HeaderCarrier): IO[Unit]
}

@Singleton
class AuditServiceImpl @Inject() (connector: AuditConnector) extends AuditService with IOFutures {

  override def balanceRequestSucceeded(auditInfo: AuditInfo, balance: Balance)(implicit
    hc: HeaderCarrier
  ): IO[Unit] = IO.executionContext.flatMap {
    implicit ec =>
      IO {
        connector.sendExplicitAudit[BalanceRequestSucceededEvent](
          AuditEventType.BalanceRequestSucceeded.name,
          BalanceRequestSucceededEvent(
            auditInfo.internalId,
            auditInfo.guaranteeReferenceNumber,
            auditInfo.balanceRequest.accessCode,
            balance
          )
        )
      }
  }

  private def grnNotFound()(implicit
    hc: HeaderCarrier,
    auditInfo: AuditInfo
  ): IO[Unit] = IO.executionContext.flatMap {
    implicit ec =>
      IO {
        connector.sendExplicitAudit[GRNNotFoundEvent](
          AuditEventType.GRNNotFound.name,
          GRNNotFoundEvent.toEvent
        )
      }
  }

  private def rateLimitExceeded()(implicit
    hc: HeaderCarrier,
    auditInfo: AuditInfo
  ): IO[Unit] = IO.executionContext.flatMap {
    implicit ec =>
      IO {
        connector.sendExplicitAudit[RateLimitedEvent](
          AuditEventType.RateLimited.name,
          RateLimitedEvent.toEvent
        )
      }
  }

  private def invalidAccessCode()(implicit
    hc: HeaderCarrier,
    auditInfo: AuditInfo
  ): IO[Unit] = IO.executionContext.flatMap {
    implicit ec =>
      IO {
        connector.sendExplicitAudit[AccessCodeNotValidEvent](
          AuditEventType.AccessCodeNotValid.name,
          AccessCodeNotValidEvent.toEvent
        )
      }
  }

  private def serverError()(implicit
    hc: HeaderCarrier,
    auditInfo: AuditInfo
  ): IO[Unit] = IO.executionContext.flatMap {
    implicit ec =>
      IO {
        connector.sendExplicitAudit[ServerErrorEvent](
          AuditEventType.ServerError.name,
          ServerErrorEvent.toEvent
        )
      }
  }

  override def balanceRequestFailed(request: AuthenticatedRequest[JsValue], grn: GuaranteeReferenceNumber)(implicit hc: HeaderCarrier): IO[Unit] =
    IO.executionContext.flatMap {
      implicit ec =>
        IO {
          connector.sendExplicitAudit[InvalidPayloadEvent](
            AuditEventType.InvalidPayload.name,
            InvalidPayloadEvent(request.internalId, grn, request.body)
          )
        }
    }

  override def balanceRequestFailed[E](originalError: E)(implicit
    auditInfo: AuditInfo,
    hc: HeaderCarrier
  ): IO[Unit] = originalError match {
    case RoutingError.InvalidAccessCode                                 => invalidAccessCode()
    case RoutingError.GuaranteeReferenceNotFound                        => grnNotFound()
    case RoutingError.Unexpected(_) | RequestLockingError.Unexpected(_) => serverError();
    case RequestLockingError.AlreadyLocked                              => rateLimitExceeded()
    case e: NonEmptyList[ValidationError]                               => invalidAccessCode()
    case _                                                              => serverError()
  }

}
