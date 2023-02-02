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

import cats.effect.unsafe.implicits.global
import models.request.AuthenticatedRequest
import models.values.InternalId
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Writes
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import v2.models.AccessCode
import v2.models.AuditInfo
import v2.models.BalanceRequest
import v2.models.BalanceRequestFailedEvent
import v2.models.BalanceRequestSucceededEvent
import v2.models.GRNNotFoundEvent
import v2.models.GuaranteeReferenceNumber
import v2.models.RateLimitedEvent
import v2.models.AuditEventType.RateLimited

import scala.concurrent.ExecutionContext

class AuditServiceSpec extends AsyncFlatSpec with Matchers with AsyncIdiomaticMockito with BeforeAndAfterEach {

  val auditConnector = mock[AuditConnector]
  val auditService   = new AuditServiceImpl(auditConnector)

  val internalId         = InternalId("ABC123")
  val accessCode         = AccessCode("1234")
  val guaranteeReference = GuaranteeReferenceNumber("05DE3300BE0001067A001017")
  val auditInfo          = AuditInfo(BalanceRequest(accessCode), guaranteeReference, internalId)

  implicit val hc = HeaderCarrier()

  override protected def beforeEach(): Unit = reset(auditConnector)

  "AuditService.balanceRequestSucceeded" should "audit a successful request" in {

    val balanceRequestSucceeded = BalanceRequestSucceededEvent(internalId, guaranteeReference, accessCode)

    auditService
      .balanceRequestSucceeded(auditInfo)
      .map {
        _ =>
          auditConnector.sendExplicitAudit[BalanceRequestSucceededEvent](
            "BalanceRequestSucceeded",
            balanceRequestSucceeded
          )(
            any[HeaderCarrier],
            any[ExecutionContext],
            any[Writes[BalanceRequestSucceededEvent]]
          ) wasCalled once
      }
      .unsafeToFuture()
  }

  "AuditService.balanceRequestFailed with guarantee reference number not found " should "audit a failed balance request" in {

    val balanceRequestFailed =
      GRNNotFoundEvent(
        auditInfo.internalId,
        auditInfo.guaranteeReferenceNumber,
        auditInfo.balanceRequest.accessCode,
        "Guarantee balance not found."
      )

    auditService
      .balanceRequestFailed[GRNNotFoundEvent](balanceRequestFailed)
      .map {
        _ =>
          auditConnector.sendExplicitAudit[GRNNotFoundEvent](
            "GRNNotFound",
            balanceRequestFailed
          )(
            any[HeaderCarrier],
            any[ExecutionContext],
            any[Writes[GRNNotFoundEvent]]
          ) wasCalled once
      }
      .unsafeToFuture()
  }

  "AuditService.balanceRequestFailed with rate limit exceeded " should "audit a failed balance request" in {

    val balanceRequestFailed =
      RateLimitedEvent(
        auditInfo.internalId,
        auditInfo.guaranteeReferenceNumber,
        auditInfo.balanceRequest.accessCode,
        "The request for the API is throttled as you have exceeded your quota."
      )

    auditService
      .balanceRequestFailed[RateLimitedEvent](balanceRequestFailed)
      .map {
        _ =>
          auditConnector.sendExplicitAudit[RateLimitedEvent](
            "RateLimited",
            balanceRequestFailed
          )(
            any[HeaderCarrier],
            any[ExecutionContext],
            any[Writes[RateLimitedEvent]]
          ) wasCalled once
      }
      .unsafeToFuture()
  }

}
