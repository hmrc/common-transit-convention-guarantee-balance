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

import cats.data.EitherT
import cats.data.NonEmptyList
import models.values.InternalId
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.http.HeaderCarrier
import v2.models.AccessCode
import v2.models.AuditInfo
import v2.models.BalanceRequest
import v2.models.GuaranteeReferenceNumber
import v2.models.errors.InternalServiceError
import v2.models.errors.PresentationError
import v2.models.errors.RequestLockingError
import v2.models.errors.RoutingError
import v2.models.errors.ValidationError
import v2.services.AuditService

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future

class ErrorTranslatorSpec extends AnyFlatSpec with Matchers with ScalaFutures with ScalaCheckDrivenPropertyChecks with BeforeAndAfterEach {

  object Harness extends ErrorTranslator

  import Harness._

  implicit val hc: HeaderCarrier            = HeaderCarrier()
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val auditInfo: AuditInfo = AuditInfo(BalanceRequest(AccessCode("1")), GuaranteeReferenceNumber("grn1"), InternalId("12345"))

  val mockAuditService: AuditService = mock[AuditService]

  override protected def beforeEach(): Unit = reset(mockAuditService)

  "ErrorConverter#asPresentation" should "for a success return the same right" in {

    val input: EitherT[Future, RequestLockingError, Unit] = EitherT.rightT[Future, RequestLockingError](())
    whenReady(input.asPresentation(auditInfo, mockAuditService).value) {
      _ shouldBe Right(())
    }
    verify(mockAuditService, times(0)).balanceRequestFailed(any)(eqTo(auditInfo), eqTo(hc), eqTo(ec))
  }

  it should "for an error returns a left with the appropriate presentation error" in {

    val error                                             = new IllegalStateException()
    val input: EitherT[Future, RequestLockingError, Unit] = EitherT.leftT[Future, Unit](RequestLockingError.Unexpected(Some(error)))
    whenReady(input.asPresentation(auditInfo, mockAuditService).value) {
      _ shouldBe Left(InternalServiceError(cause = Some(error)))

    }
    verify(mockAuditService, times(1)).balanceRequestFailed(eqTo(RequestLockingError.Unexpected(Some(error))))(eqTo(auditInfo), eqTo(hc), eqTo(ec))
  }

  "ValidationError" should "return a bad request when any error is provided" in forAll(
    Gen.oneOf(
      NonEmptyList.one(ValidationError.InvalidAccessCodeLength(AccessCode("ABCD?"))),
      NonEmptyList.one(ValidationError.InvalidAccessCodeCharacters(AccessCode("ABCD?"))),
      NonEmptyList.of(
        ValidationError.InvalidAccessCodeCharacters(AccessCode("ABCD?")),
        ValidationError.InvalidAccessCodeLength(AccessCode("ABCD?"))
      )
    )
  ) {
    error =>
      validationErrorConverter.convert(error) shouldBe
        PresentationError.badRequestError(s"Access code ABCD? must be four alphanumeric characters.")
  }

  "RequestLockingError" should "return a rate limited error if we are rate limited" in {
    requestLockingErrorConverter.convert(RequestLockingError.AlreadyLocked) shouldBe
      PresentationError.rateLimitedRequest()
  }

  it should "return an Unexpected if an exception occurs" in {
    val exception = new IllegalStateException()
    requestLockingErrorConverter.convert(RequestLockingError.Unexpected(thr = Some(exception))) shouldBe
      PresentationError.internalServiceError(cause = Some(exception))
  }

  "RoutingError" should "return a not found if we don't find the GRN" in {
    routingErrorConverter.convert(RoutingError.GuaranteeReferenceNotFound) shouldBe
      PresentationError.notFoundError("The guarantee reference number or access code did not match an existing guarantee.")
  }

  it should "return a not found if the access code does not match" in {
    routingErrorConverter.convert(RoutingError.InvalidAccessCode) shouldBe
      PresentationError.notFoundError("The guarantee reference number or access code did not match an existing guarantee.")
  }

  it should "return a bad request if the type is not supported" in {
    routingErrorConverter.convert(RoutingError.InvalidGuaranteeType) shouldBe
      PresentationError.invalidGuaranteeTypeError("Guarantee type is not supported.")
  }

  it should "return an Unexpected if an exception occurs" in {
    val exception = new IllegalStateException()
    routingErrorConverter.convert(RoutingError.Unexpected(thr = Some(exception))) shouldBe
      PresentationError.internalServiceError(cause = Some(exception))
  }

}
