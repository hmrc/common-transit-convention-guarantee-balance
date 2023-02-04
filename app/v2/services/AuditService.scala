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

import cats.effect.IO
import com.google.inject.ImplementedBy
import runtime.IOFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import v2.models.AuditEventType
import v2.models.AuditInfo
import v2.models.BalanceRequestSucceededEvent
import v2.models.GRNNotFoundEvent
import v2.models.RateLimitedEvent

import javax.inject._

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {

  def balanceRequestSucceeded(auditInfo: AuditInfo)(implicit
    hc: HeaderCarrier
  ): IO[Unit]

  def balanceRequestFailed[E](error: E)(implicit
    auditInfo: AuditInfo,
    hc: HeaderCarrier
  ): IO[Unit]
}

@Singleton
class AuditServiceImpl @Inject() (connector: AuditConnector) extends AuditService with IOFutures {

  def balanceRequestSucceeded(auditInfo: AuditInfo)(implicit
    hc: HeaderCarrier
  ): IO[Unit] = IO.executionContext.flatMap {
    implicit ec =>
      IO {
        connector.sendExplicitAudit[BalanceRequestSucceededEvent](
          AuditEventType.BalanceRequestSucceeded.name,
          BalanceRequestSucceededEvent(
            auditInfo.internalId,
            auditInfo.guaranteeReferenceNumber,
            auditInfo.balanceRequest.accessCode
          )
        )
      }
  }

  def grnNotFound(event: GRNNotFoundEvent)(implicit
    hc: HeaderCarrier
  ): IO[Unit] = IO.executionContext.flatMap {
    implicit ec =>
      IO {
        connector.sendExplicitAudit[GRNNotFoundEvent](
          AuditEventType.GRNNotFound.name,
          GRNNotFoundEvent(
            event.userInternalId,
            event.guaranteeReference,
            event.accessCode,
            event.reason
          )
        )
      }
  }

  def rateLimitExceeded(event: RateLimitedEvent)(implicit
    hc: HeaderCarrier
  ): IO[Unit] = IO.executionContext.flatMap {
    implicit ec =>
      IO {
        connector.sendExplicitAudit[RateLimitedEvent](
          AuditEventType.RateLimited.name,
          RateLimitedEvent(
            event.userInternalId,
            event.guaranteeReference,
            event.accessCode,
            event.reason
          )
        )
      }
  }

  // TODO: HERE
  def balanceRequestFailed[E](originalError: E)(implicit
    auditInfo: AuditInfo,
    hc: HeaderCarrier
  ): IO[Unit] = originalError match {
    case ev: GRNNotFoundEvent => grnNotFound(ev)
    case ev: RateLimitedEvent => rateLimitExceeded(ev)
    case _                    => IO(())
  }

}
