/*
 * Copyright 2022 HM Revenue & Customs
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

package services

import cats.effect.unsafe.implicits.global
import models.audit.RateLimitedRequestEvent
import models.request.AuthenticatedRequest
import models.request.BalanceRequest
import models.values.AccessCode
import models.values.GuaranteeReference
import models.values.InternalId
import models.values.TaxIdentifier
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.JsValue
import play.api.libs.json.Writes
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext

class AuditServiceSpec extends AsyncFlatSpec with Matchers with AsyncIdiomaticMockito with BeforeAndAfterEach {

  val auditConnector = mock[AuditConnector]
  val auditService   = new AuditServiceImpl(auditConnector)

  implicit val hc = HeaderCarrier()

  val internalId         = InternalId("ABC123")
  val taxIdentifier      = TaxIdentifier("GB12345678900")
  val guaranteeReference = GuaranteeReference("05DE3300BE0001067A001017")
  val accessCode         = AccessCode("1234")

  override protected def beforeEach(): Unit =
    reset(auditConnector)

  "AuditService.auditRateLimitedRequest" should "audit rate limited requests" in {

    // Given this request with a specific internal ID
    val authenticatedRequest = mock[AuthenticatedRequest[JsValue]]
    authenticatedRequest.internalId returns internalId

    val balanceRequest = BalanceRequest(taxIdentifier, guaranteeReference, accessCode)

    // When an audit request is made
    auditService.auditRateLimitedRequest(authenticatedRequest, balanceRequest)

    // Then the connector should be called with this event
    val expectedEvent =
      RateLimitedRequestEvent(internalId, taxIdentifier, guaranteeReference, accessCode)

    auditService
      .auditRateLimitedRequest(authenticatedRequest, balanceRequest)
      .map {
        _ =>
          auditConnector.sendExplicitAudit[RateLimitedRequestEvent](
            "RateLimitedRequest",
            expectedEvent
          )(
            any[HeaderCarrier],
            any[ExecutionContext],
            any[Writes[RateLimitedRequestEvent]]
          ) wasCalled once
      }
      .unsafeToFuture()
  }

}
