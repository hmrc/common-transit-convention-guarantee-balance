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

import cats.arrow.FunctionK
import cats.data._
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.implicits.global
import config.AppConfig
import controllers.actions._
import metrics.FakeMetrics
import models.request.AuthenticatedRequest
import models.values.InternalId
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.MockitoSugar
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.HeaderNames
import play.api.http.Status._
import play.api.libs.json._
import play.api.mvc.Request
import play.api.test._
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.http.HeaderCarrier
import v2.generators.Generators
import v2.models._
import v2.models.errors._
import v2.services._

import scala.concurrent.ExecutionContext

class GuaranteeBalanceControllerSpec extends AnyFlatSpec with Matchers with MockitoSugar with ScalaFutures with ScalaCheckDrivenPropertyChecks with Generators {

  private def fakeAuthActionProvider(internalId: InternalId): FakeAuthActionProvider =
    new FakeAuthActionProvider(
      new FakeAuthAction(new FunctionK[Request, AuthenticatedRequest] {

        override def apply[A](fa: Request[A]): AuthenticatedRequest[A] =
          AuthenticatedRequest(fa, internalId)
      }),
      stubControllerComponents()
    )

  implicit val hc = HeaderCarrier()
  implicit val ec = ExecutionContext.global

  "GuaranteeBalanceController#postRequest" should "return OK with a result if everything is okay" in forAll(
    arbitrary[InternalId],
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalBalanceResponse]
  ) {
    (internalId, grn, internalBalanceResponse) =>
      val request = FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode" -> "1"
        )
      )

      val mockLockService = mock[RequestLockingService]
      when(mockLockService.lock(GuaranteeReferenceNumber(eqTo(grn.value)), InternalId(eqTo(internalId.value))))
        .thenReturn(EitherT.rightT[IO, RequestLockingError](()))

      val mockValidationService = mock[ValidationService]
      when(mockValidationService.validate(eqTo(BalanceRequest(AccessCode("1"))))).thenReturn(EitherT.rightT[IO, NonEmptyList[ValidationError]](()))

      val mockRouterService = mock[RouterService]
      when(mockRouterService.request(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(BalanceRequest(AccessCode("1"))))(any()))
        .thenReturn(EitherT.rightT[IO, RoutingError](internalBalanceResponse))

      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.enablePhase5).thenReturn(true)

      val mockAuditService = mock[AuditServiceImpl]

      val sut = new GuaranteeBalanceController(
        mockAppConfig,
        fakeAuthActionProvider(internalId),
        mockLockService,
        mockValidationService,
        mockRouterService,
        mockAuditService,
        stubControllerComponents(),
        IORuntime.global,
        new FakeMetrics
      )

      val result = sut.postRequest(grn)(request = request)
      status(result) shouldBe OK
      whenReady(HateoasResponse(grn, internalBalanceResponse).unsafeToFuture()) {
        r => contentAsJson(result) shouldBe r
      }

      verify(mockAppConfig, times(1)).enablePhase5
      verify(mockAuditService, times(1)).balanceRequestSucceeded(any(), Balance(anyDouble()))(any(), any())
      verify(mockAuditService, times(0)).balanceRequestFailed(any())(any(), any(), any())
      verify(mockAuditService, times(0)).invalidPayloadBalanceRequest(any(), GuaranteeReferenceNumber(anyString()))(any(), any())
  }

  it should "return OK with a result if everything is okay but we have additional junk in the JSON payload" in forAll(
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalId],
    arbitrary[InternalBalanceResponse]
  ) {
    (grn, internalId, internalBalanceResponse) =>
      val request = FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode"   -> "1",
          "this-is-junk" -> "bleh"
        )
      )

      val mockLockService = mock[RequestLockingService]
      when(mockLockService.lock(GuaranteeReferenceNumber(eqTo(grn.value)), InternalId(eqTo(internalId.value))))
        .thenReturn(EitherT.rightT[IO, RequestLockingError](()))

      val mockValidationService = mock[ValidationService]
      when(mockValidationService.validate(eqTo(BalanceRequest(AccessCode("1"))))).thenReturn(EitherT.rightT[IO, NonEmptyList[ValidationError]](()))

      val mockRouterService = mock[RouterService]
      when(mockRouterService.request(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(BalanceRequest(AccessCode("1"))))(any()))
        .thenReturn(EitherT.rightT[IO, RoutingError](internalBalanceResponse))

      val mockAuditService = mock[AuditService]

      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.enablePhase5).thenReturn(true)

      val sut = new GuaranteeBalanceController(
        mockAppConfig,
        fakeAuthActionProvider(internalId),
        mockLockService,
        mockValidationService,
        mockRouterService,
        mockAuditService,
        stubControllerComponents(),
        IORuntime.global,
        new FakeMetrics
      )

      val result = sut.postRequest(grn)(request = request)
      status(result) shouldBe OK
      whenReady(HateoasResponse(grn, internalBalanceResponse).unsafeToFuture()) {
        r => contentAsJson(result) shouldBe r
      }
      verify(mockAppConfig, times(1)).enablePhase5
      verify(mockAuditService, times(1)).balanceRequestSucceeded(any(), Balance(any()))(any(), any())
      verify(mockAuditService, times(0)).balanceRequestFailed(any())(any(), any(), any())
      verify(mockAuditService, times(0)).invalidPayloadBalanceRequest(any(), GuaranteeReferenceNumber(anyString()))(any(), any())
  }

  it should "return not acceptable if the accept header is wrong" in forAll(
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalId],
    arbitrary[InternalBalanceResponse]
  ) {
    (grn, internalId, internalBalanceResponse) =>
      val request = AuthenticatedRequest(
        FakeRequest(
          "POST",
          "/",
          FakeHeaders(
            Seq(
              HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json"
            )
          ),
          Json.obj(
            "accessCode" -> "1"
          )
        ),
        internalId
      )

      val mockLockService = mock[RequestLockingService]
      when(mockLockService.lock(GuaranteeReferenceNumber(eqTo(grn.value)), InternalId(eqTo(internalId.value))))
        .thenReturn(EitherT.rightT[IO, RequestLockingError](()))

      val mockValidationService = mock[ValidationService]
      when(mockValidationService.validate(eqTo(BalanceRequest(AccessCode("1"))))).thenReturn(EitherT.rightT[IO, NonEmptyList[ValidationError]](()))

      val mockRouterService = mock[RouterService]
      when(mockRouterService.request(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(BalanceRequest(AccessCode("1"))))(any()))
        .thenReturn(EitherT.rightT[IO, RoutingError](internalBalanceResponse))

      val mockAuditService = mock[AuditService]

      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.enablePhase5).thenReturn(true)

      val sut = new GuaranteeBalanceController(
        mockAppConfig,
        FakeAuthActionProvider,
        mockLockService,
        mockValidationService,
        mockRouterService,
        mockAuditService,
        stubControllerComponents(),
        IORuntime.global,
        new FakeMetrics
      )

      val result = sut.postRequest(grn)(request = request)
      status(result) shouldBe NOT_ACCEPTABLE
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "NOT_ACCEPTABLE",
        "message" -> "The accept header must be set to application/vnd.hmrc.2.0+json to use this resource."
      )

      verify(mockAppConfig, times(1)).enablePhase5
      verify(mockAuditService, times(0)).balanceRequestSucceeded(any(), Balance(anyDouble()))(any(), any())
      verify(mockAuditService, times(0)).balanceRequestFailed(any())(any(), any(), any())
      verify(mockAuditService, times(1)).invalidPayloadBalanceRequest(any(), GuaranteeReferenceNumber(anyString()))(any(), any())
  }

  it should "return Too Many Requests if rate limiting is in effect" in forAll(
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalId],
    arbitrary[InternalBalanceResponse]
  ) {
    (grn, internalId, internalBalanceResponse) =>
      val request = FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode" -> "1"
        )
      )

      val mockLockService = mock[RequestLockingService]
      when(mockLockService.lock(GuaranteeReferenceNumber(eqTo(grn.value)), InternalId(eqTo(internalId.value))))
        .thenReturn(EitherT.leftT[IO, Unit](RequestLockingError.AlreadyLocked))

      val mockValidationService = mock[ValidationService]
      when(mockValidationService.validate(eqTo(BalanceRequest(AccessCode("1"))))).thenReturn(EitherT.rightT[IO, NonEmptyList[ValidationError]](()))

      val mockRouterService = mock[RouterService]
      when(mockRouterService.request(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(BalanceRequest(AccessCode("1"))))(any()))
        .thenReturn(EitherT.rightT[IO, RoutingError](internalBalanceResponse))

      val mockAuditService = mock[AuditService]

      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.enablePhase5).thenReturn(true)

      val sut = new GuaranteeBalanceController(
        mockAppConfig,
        fakeAuthActionProvider(internalId),
        mockLockService,
        mockValidationService,
        mockRouterService,
        mockAuditService,
        stubControllerComponents(),
        IORuntime.global,
        new FakeMetrics
      )

      val result = sut.postRequest(grn)(request = request)
      status(result) shouldBe TOO_MANY_REQUESTS
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "MESSAGE_THROTTLED_OUT",
        "message" -> "The request for the API is throttled as you have exceeded your quota."
      )

      verify(mockAppConfig, times(1)).enablePhase5
      verify(mockAuditService, times(0)).balanceRequestSucceeded(any(), Balance(anyDouble()))(any(), any())
      verify(mockAuditService, times(1)).balanceRequestFailed(eqTo(RequestLockingError.AlreadyLocked))(any(), any(), any())
      verify(mockAuditService, times(0)).invalidPayloadBalanceRequest(any(), GuaranteeReferenceNumber(anyString()))(any(), any())
  }

  it should "return an error if the body cannot be parsed correctly" in forAll(
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalId],
    arbitrary[InternalBalanceResponse]
  ) {
    (grn, internalId, internalBalanceResponse) =>
      val request: AuthenticatedRequest[JsValue] =
        AuthenticatedRequest(
          FakeRequest(
            "POST",
            "/",
            FakeHeaders(
              Seq(
                HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
              )
            ),
            Json.obj(
              "incorrect" -> "1"
            )
          ),
          internalId
        )

      val mockLockService = mock[RequestLockingService]
      when(mockLockService.lock(GuaranteeReferenceNumber(eqTo(grn.value)), InternalId(eqTo(internalId.value))))
        .thenReturn(EitherT.rightT[IO, RequestLockingError](()))

      val mockValidationService = mock[ValidationService]
      when(mockValidationService.validate(eqTo(BalanceRequest(AccessCode("1"))))).thenReturn(EitherT.rightT[IO, NonEmptyList[ValidationError]](()))

      val mockRouterService = mock[RouterService]
      when(mockRouterService.request(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(BalanceRequest(AccessCode("1"))))(any()))
        .thenReturn(EitherT.rightT[IO, RoutingError](internalBalanceResponse))

      val mockAuditService = mock[AuditService]

      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.enablePhase5).thenReturn(true)

      val sut = new GuaranteeBalanceController(
        mockAppConfig,
        fakeAuthActionProvider(internalId),
        mockLockService,
        mockValidationService,
        mockRouterService,
        mockAuditService,
        stubControllerComponents(),
        IORuntime.global,
        new FakeMetrics
      )

      val result = sut.postRequest(grn)(request = request)
      status(result) shouldBe BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "BAD_REQUEST",
        "message" -> "The access code was not supplied."
      )

      verify(mockAppConfig, times(1)).enablePhase5
      verify(mockAuditService, times(0)).balanceRequestSucceeded(any(), Balance(anyDouble()))(any(), any())
      verify(mockAuditService, times(0)).balanceRequestFailed(any())(any(), any(), any())
      verify(mockAuditService, times(1)).invalidPayloadBalanceRequest(any(), GuaranteeReferenceNumber(anyString()))(any(), any())

  }

  it should "return BAD_REQUEST if the access code is not valid" in forAll(
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalId],
    arbitrary[InternalBalanceResponse]
  ) {
    (grn, internalId, internalBalanceResponse) =>
      val request = FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode" -> "1"
        )
      )

      val mockLockService = mock[RequestLockingService]
      when(mockLockService.lock(GuaranteeReferenceNumber(eqTo(grn.value)), InternalId(eqTo(internalId.value))))
        .thenReturn(EitherT.rightT[IO, RequestLockingError](()))

      val mockValidationService = mock[ValidationService]
      when(mockValidationService.validate(eqTo(BalanceRequest(AccessCode("1")))))
        .thenReturn(EitherT.leftT[IO, Unit](NonEmptyList.one(ValidationError.InvalidAccessCodeLength(AccessCode("1")))))

      val mockRouterService = mock[RouterService]
      when(mockRouterService.request(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(BalanceRequest(AccessCode("1"))))(any()))
        .thenReturn(EitherT.rightT[IO, RoutingError](internalBalanceResponse))

      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.enablePhase5).thenReturn(true)

      val mockAuditService = mock[AuditService]

      val sut = new GuaranteeBalanceController(
        mockAppConfig,
        fakeAuthActionProvider(internalId),
        mockLockService,
        mockValidationService,
        mockRouterService,
        mockAuditService,
        stubControllerComponents(),
        IORuntime.global,
        new FakeMetrics
      )

      val result = sut.postRequest(grn)(request = request)
      status(result) shouldBe BAD_REQUEST
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "BAD_REQUEST",
        "message" -> "Access code 1 must be four alphanumeric characters."
      )

      verify(mockAppConfig, times(1)).enablePhase5
      verify(mockAuditService, times(0)).balanceRequestSucceeded(any(), Balance(anyDouble()))(any(), any())
      verify(mockAuditService, times(1)).balanceRequestFailed(any())(any(), any(), any())
      verify(mockAuditService, times(0)).invalidPayloadBalanceRequest(any(), GuaranteeReferenceNumber(anyString()))(any(), any())
  }

  "GuaranteeBalanceController#postRequest" should "return NOT_FOUND if the GRN was not found, or if the access code was not valid" in forAll(
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalId]
  ) {
    (grn, internalId) =>
      val request = FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode" -> "1"
        )
      )

      val mockLockService = mock[RequestLockingService]
      when(mockLockService.lock(GuaranteeReferenceNumber(eqTo(grn.value)), InternalId(eqTo(internalId.value))))
        .thenReturn(EitherT.rightT[IO, RequestLockingError](()))

      val mockValidationService = mock[ValidationService]
      when(mockValidationService.validate(eqTo(BalanceRequest(AccessCode("1"))))).thenReturn(EitherT.rightT[IO, NonEmptyList[ValidationError]](()))

      val mockRouterService = mock[RouterService]
      when(mockRouterService.request(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(BalanceRequest(AccessCode("1"))))(any()))
        .thenReturn(EitherT.leftT[IO, InternalBalanceResponse](RoutingError.GuaranteeReferenceNotFound))

      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.enablePhase5).thenReturn(true)

      val mockAuditService = mock[AuditService]

      val sut = new GuaranteeBalanceController(
        mockAppConfig,
        fakeAuthActionProvider(internalId),
        mockLockService,
        mockValidationService,
        mockRouterService,
        mockAuditService,
        stubControllerComponents(),
        IORuntime.global,
        new FakeMetrics
      )

      val result = sut.postRequest(grn)(request = request)
      status(result) shouldBe NOT_FOUND
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "NOT_FOUND",
        "message" -> "The guarantee reference number or access code did not match an existing guarantee."
      )

      verify(mockAppConfig, times(1)).enablePhase5
      verify(mockAuditService, times(0)).balanceRequestSucceeded(any(), Balance(anyDouble()))(any(), any())
      verify(mockAuditService, times(1)).balanceRequestFailed(eqTo(RoutingError.GuaranteeReferenceNotFound))(any(), any(), any())
      verify(mockAuditService, times(0)).invalidPayloadBalanceRequest(any(), GuaranteeReferenceNumber(anyString()))(any(), any())
  }

  "GuaranteeBalanceController#postRequest" should "return NOT_FOUND if the access code was not valid" in forAll(
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalId]
  ) {
    (grn, internalId) =>
      val request = FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode" -> "1"
        )
      )

      val mockLockService = mock[RequestLockingService]
      when(mockLockService.lock(GuaranteeReferenceNumber(eqTo(grn.value)), InternalId(eqTo(internalId.value))))
        .thenReturn(EitherT.rightT[IO, RequestLockingError](()))

      val mockValidationService = mock[ValidationService]
      when(mockValidationService.validate(eqTo(BalanceRequest(AccessCode("1"))))).thenReturn(EitherT.rightT[IO, NonEmptyList[ValidationError]](()))

      val mockRouterService = mock[RouterService]
      when(mockRouterService.request(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(BalanceRequest(AccessCode("1"))))(any()))
        .thenReturn(EitherT.leftT[IO, InternalBalanceResponse](RoutingError.InvalidAccessCode))

      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.enablePhase5).thenReturn(true)

      val mockAuditService = mock[AuditService]

      val sut = new GuaranteeBalanceController(
        mockAppConfig,
        fakeAuthActionProvider(internalId),
        mockLockService,
        mockValidationService,
        mockRouterService,
        mockAuditService,
        stubControllerComponents(),
        IORuntime.global,
        new FakeMetrics
      )

      val result = sut.postRequest(grn)(request = request)
      status(result) shouldBe NOT_FOUND
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "NOT_FOUND",
        "message" -> "The guarantee reference number or access code did not match an existing guarantee."
      )

      verify(mockAppConfig, times(1)).enablePhase5
      verify(mockAuditService, times(0)).balanceRequestSucceeded(any(), Balance(anyDouble()))(any(), any())
      verify(mockAuditService, times(1)).balanceRequestFailed(eqTo(RoutingError.InvalidAccessCode))(any(), any(), any())
      verify(mockAuditService, times(0)).invalidPayloadBalanceRequest(any(), GuaranteeReferenceNumber(anyString()))(any(), any())
  }

  "GuaranteeBalanceController#postRequest" should "return NOT_ACCEPTABLE if Phase 5 is disabled" in forAll(
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalId]
  ) {
    (grn, internalId) =>
      val request = FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode" -> "1"
        )
      )

      val mockLockService = mock[RequestLockingService]
      when(mockLockService.lock(GuaranteeReferenceNumber(eqTo(grn.value)), InternalId(eqTo(internalId.value))))
        .thenReturn(EitherT.rightT[IO, RequestLockingError](()))

      val mockValidationService = mock[ValidationService]
      when(mockValidationService.validate(eqTo(BalanceRequest(AccessCode("1"))))).thenReturn(EitherT.rightT[IO, NonEmptyList[ValidationError]](()))

      val mockRouterService = mock[RouterService]
      when(mockRouterService.request(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(BalanceRequest(AccessCode("1"))))(any()))
        .thenReturn(EitherT.leftT[IO, InternalBalanceResponse](RoutingError.InvalidAccessCode))

      val mockAppConfig = mock[AppConfig]
      when(mockAppConfig.enablePhase5).thenReturn(false)

      val mockAuditService = mock[AuditService]

      val sut = new GuaranteeBalanceController(
        mockAppConfig,
        fakeAuthActionProvider(internalId),
        mockLockService,
        mockValidationService,
        mockRouterService,
        mockAuditService,
        stubControllerComponents(),
        IORuntime.global,
        new FakeMetrics
      )

      val result = sut.postRequest(grn)(request = request)

      status(result) shouldBe NOT_ACCEPTABLE
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "NOT_ACCEPTABLE",
        "message" -> "CTC Traders API version 2 is not yet available. Please continue to use version 1 to submit transit messages."
      )

      verify(mockAppConfig, times(1)).enablePhase5
      verifyZeroInteractions(mockLockService)
      verifyZeroInteractions(mockValidationService)
      verifyZeroInteractions(mockRouterService)
      verifyZeroInteractions(mockAuditService)
  }

}
