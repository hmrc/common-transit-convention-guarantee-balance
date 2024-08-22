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
import models.values.InternalId
import org.mockito.scalatest.AsyncIdiomaticMockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Writes
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import v2.models.AccessCode
import v2.models.AuditInfo
import v2.models.Balance
import v2.models.BalanceRequest
import v2.models.ErrorResponseEvent
import v2.models.GuaranteeReferenceNumber
import v2.models.RateLimitedRequestEvent
import v2.models.SuccessResponseEvent
import v2.models.errors.RequestLockingError
import v2.models.errors.RoutingError
import v2.models.errors.ValidationError

import scala.concurrent.ExecutionContext

class AuditServiceSpec extends AsyncFlatSpec with Matchers with AsyncIdiomaticMockito with BeforeAndAfterEach {

  val auditConnector = mock[AuditConnector]
  val auditService   = new AuditServiceImpl(auditConnector)

  val internalId         = InternalId("ABC123")
  val accessCode         = AccessCode("1234")
  val guaranteeReference = GuaranteeReferenceNumber("05DE3300BE0001067A001017")
  val balance            = Balance(123.45)
  implicit val auditInfo = AuditInfo(BalanceRequest(accessCode), guaranteeReference, internalId)

  implicit val hc = HeaderCarrier()

  override protected def beforeEach(): Unit = reset(auditConnector)

  "AuditService.balanceRequestSucceeded" should "audit a successful request" in {

    val balanceRequestSucceeded = SuccessResponseEvent(internalId, guaranteeReference, accessCode, balance)

    auditService
      .balanceRequestSucceeded(auditInfo, balance)

    auditConnector.sendExplicitAudit[SuccessResponseEvent](
      "SuccessResponse",
      balanceRequestSucceeded
    )(
      any[HeaderCarrier],
      any[ExecutionContext],
      any[Writes[SuccessResponseEvent]]
    ) wasCalled once

  }

  "AuditService.balanceRequestFailed with guarantee reference number not found " should "audit a failed balance request" in {

    val balanceRequestFailed =
      ErrorResponseEvent(
        auditInfo.internalId,
        auditInfo.guaranteeReferenceNumber,
        auditInfo.balanceRequest.accessCode,
        "Guarantee Reference Number NotFound"
      )

    auditService
      .balanceRequestFailed(RoutingError.GuaranteeReferenceNotFound)

    auditConnector.sendExplicitAudit[ErrorResponseEvent](
      "ErrorResponse",
      balanceRequestFailed
    )(
      any[HeaderCarrier],
      any[ExecutionContext],
      any[Writes[ErrorResponseEvent]]
    ) wasCalled once

  }

  "AuditService.balanceRequestFailed with rate limit exceeded " should "audit a failed balance request" in {

    val balanceRequestFailed =
      RateLimitedRequestEvent(
        auditInfo.internalId,
        auditInfo.guaranteeReferenceNumber,
        auditInfo.balanceRequest.accessCode
      )

    auditService
      .balanceRequestFailed(RequestLockingError.AlreadyLocked)

    auditConnector.sendExplicitAudit[RateLimitedRequestEvent](
      "RateLimitedRequest",
      balanceRequestFailed
    )(
      any[HeaderCarrier],
      any[ExecutionContext],
      any[Writes[RateLimitedRequestEvent]]
    ) wasCalled once

  }

  "AuditService.balanceRequestFailed with routing invalid access code" should "audit a failed balance request" in {

    val balanceRequestFailed =
      ErrorResponseEvent(
        auditInfo.internalId,
        auditInfo.guaranteeReferenceNumber,
        auditInfo.balanceRequest.accessCode,
        "Invalid Access Code"
      )

    auditService
      .balanceRequestFailed(RoutingError.InvalidAccessCode)

    auditConnector.sendExplicitAudit[ErrorResponseEvent](
      "ErrorResponse",
      balanceRequestFailed
    )(
      any[HeaderCarrier],
      any[ExecutionContext],
      any[Writes[ErrorResponseEvent]]
    ) wasCalled once

  }

  "AuditService.balanceRequestFailed with routing unexpected error" should "audit a failed balance request" in {

    val balanceRequestFailed =
      ErrorResponseEvent(
        auditInfo.internalId,
        auditInfo.guaranteeReferenceNumber,
        auditInfo.balanceRequest.accessCode,
        "Server Error"
      )

    auditService
      .balanceRequestFailed(RoutingError.Unexpected(Some(new Exception("unexpected routing error"))))

    auditConnector.sendExplicitAudit[ErrorResponseEvent](
      "ErrorResponse",
      balanceRequestFailed
    )(
      any[HeaderCarrier],
      any[ExecutionContext],
      any[Writes[ErrorResponseEvent]]
    ) wasCalled once
  }

  "AuditService.balanceRequestFailed with ValidationError" should "audit a failed balance request" in {

    val balanceRequestFailed =
      ErrorResponseEvent(
        auditInfo.internalId,
        auditInfo.guaranteeReferenceNumber,
        auditInfo.balanceRequest.accessCode,
        "Invalid Access Code"
      )

    auditService
      .balanceRequestFailed(NonEmptyList.one(ValidationError.InvalidAccessCodeLength(accessCode)))

    auditConnector.sendExplicitAudit[ErrorResponseEvent](
      "ErrorResponse",
      balanceRequestFailed
    )(
      any[HeaderCarrier],
      any[ExecutionContext],
      any[Writes[ErrorResponseEvent]]
    ) wasCalled once

  }

}
