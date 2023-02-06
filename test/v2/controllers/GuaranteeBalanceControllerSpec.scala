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
import cats.data.EitherT
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.implicits.global
import controllers.actions.FakeAuthAction
import controllers.actions.FakeAuthActionProvider
import metrics.FakeMetrics
import models.request.AuthenticatedRequest
import models.values.InternalId
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.MockitoSugar
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.HeaderNames
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.NOT_ACCEPTABLE
import play.api.http.Status.NOT_FOUND
import play.api.http.Status.OK
import play.api.http.Status.TOO_MANY_REQUESTS
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.http.HeaderCarrier
import v2.generators.Generators
import v2.models.AccessCode
import v2.models.AuditInfo
import v2.models.Balance
import v2.models.BalanceRequest
import v2.models.GuaranteeReferenceNumber
import v2.models.HateoasResponse
import v2.models.InternalBalanceResponse
import v2.models.errors.RequestLockingError
import v2.models.errors.RoutingError
import v2.models.errors.ValidationError
import v2.services.AuditService
import v2.services.RequestLockingService
import v2.services.RouterService
import v2.services.ValidationService

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

  "GuaranteeBalanceController#postRequest" should "return OK with a result if everything is okay" in forAll(
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
        .thenReturn(EitherT.rightT[IO, RoutingError](InternalBalanceResponse(Balance(3.14))))

      val mockAuditService = mock[AuditService]
      when(
        mockAuditService.balanceRequestSucceeded(
          eqTo(AuditInfo(BalanceRequest(AccessCode("1")), GuaranteeReferenceNumber(grn.value), InternalId(internalId.value))),
          Balance(eqTo[Double](3.14))
        )(any())
      ).thenReturn(IO(()))

      val sut = new GuaranteeBalanceController(
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
      whenReady(HateoasResponse(grn, InternalBalanceResponse(Balance(3.14))).unsafeToFuture()) {
        r => contentAsJson(result) shouldBe r
      }
      verify(mockAuditService, times(1)).balanceRequestSucceeded(any(), Balance(anyDouble()))(any())
  }

  it should "return OK with a result if everything is okay but we have additional junk in the JSON payload" in forAll(
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
        .thenReturn(EitherT.rightT[IO, RoutingError](InternalBalanceResponse(Balance(3.14))))

      val mockAuditService = mock[AuditService]
      when(
        mockAuditService.balanceRequestSucceeded(
          eqTo(AuditInfo(BalanceRequest(AccessCode("1")), GuaranteeReferenceNumber(grn.value), InternalId(internalId.value))),
          Balance(eqTo[Double](3.14))
        )(any())
      ).thenReturn(IO(()))

      val sut = new GuaranteeBalanceController(
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
      whenReady(HateoasResponse(grn, InternalBalanceResponse(Balance(3.14))).unsafeToFuture()) {
        r => contentAsJson(result) shouldBe r
      }
      verify(mockAuditService, times(1)).balanceRequestSucceeded(any(), Balance(any()))(any())
  }

  it should "return not acceptable if the accept header is wrong" in forAll(
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalId]
  ) {
    (grn, internalId) =>
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
        .thenReturn(EitherT.rightT[IO, RoutingError](InternalBalanceResponse(Balance(3.14))))

      val mockAuditService = mock[AuditService]
      when(
        mockAuditService.balanceRequestSucceeded(
          eqTo(AuditInfo(BalanceRequest(AccessCode("1")), GuaranteeReferenceNumber(grn.value), InternalId(internalId.value))),
          Balance(eqTo(123.45))
        )(any())
      ).thenReturn(IO(()))

      val sut = new GuaranteeBalanceController(
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
      verify(mockAuditService, times(0)).balanceRequestSucceeded(any(), Balance(anyDouble()))(any())
  }

  it should "return Too Many Requests if rate limiting is in effect" in forAll(
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
        .thenReturn(EitherT.leftT[IO, Unit](RequestLockingError.AlreadyLocked))

      val mockValidationService = mock[ValidationService]
      when(mockValidationService.validate(eqTo(BalanceRequest(AccessCode("1"))))).thenReturn(EitherT.rightT[IO, NonEmptyList[ValidationError]](()))

      val mockRouterService = mock[RouterService]
      when(mockRouterService.request(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(BalanceRequest(AccessCode("1"))))(any()))
        .thenReturn(EitherT.rightT[IO, RoutingError](InternalBalanceResponse(Balance(3.14))))

      val mockAuditService = mock[AuditService]
      when(
        mockAuditService.balanceRequestFailed(eqTo(RequestLockingError.AlreadyLocked))(any(), any())
      ).thenReturn(IO(()))

      val sut = new GuaranteeBalanceController(
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

      verify(mockAuditService, times(1)).balanceRequestFailed(eqTo(RequestLockingError.AlreadyLocked))(any(), any())
  }

  it should "return an error if the body cannot be parsed correctly" in forAll(
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalId]
  ) {
    (grn, internalId) =>
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
        .thenReturn(EitherT.rightT[IO, RoutingError](InternalBalanceResponse(Balance(3.14))))

      val mockAuditService = mock[AuditService]
      when(
        mockAuditService.balanceRequestFailed(eqTo(request), GuaranteeReferenceNumber(eqTo(grn.value)))(any())
      ).thenReturn(IO(()))

      val sut = new GuaranteeBalanceController(
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

  }

  it should "return BAD_REQUEST if the access code is not valid" in forAll(
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
      when(mockValidationService.validate(eqTo(BalanceRequest(AccessCode("1")))))
        .thenReturn(EitherT.leftT[IO, Unit](NonEmptyList.one(ValidationError.InvalidAccessCodeLength(AccessCode("1")))))

      val mockRouterService = mock[RouterService]
      when(mockRouterService.request(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(BalanceRequest(AccessCode("1"))))(any()))
        .thenReturn(EitherT.rightT[IO, RoutingError](InternalBalanceResponse(Balance(3.14))))

      val mockAuditService = mock[AuditService]
      when(
        mockAuditService.balanceRequestFailed(eqTo(NonEmptyList.one(ValidationError.InvalidAccessCodeLength(AccessCode("1")))))(any(), any())
      ).thenReturn(IO(()))

      val sut = new GuaranteeBalanceController(
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

      verify(mockAuditService, times(1))
        .balanceRequestFailed(eqTo(NonEmptyList.one(ValidationError.InvalidAccessCodeLength(AccessCode("1")))))(any(), any())
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

      val mockAuditService = mock[AuditService]
      when(
        mockAuditService.balanceRequestFailed(eqTo((RoutingError.GuaranteeReferenceNotFound)))(any(), any())
      ).thenReturn(IO(()))

      val sut = new GuaranteeBalanceController(
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
        "message" -> "Guarantee balance not found."
      )

      verify(mockAuditService, times(1))
        .balanceRequestFailed(eqTo(RoutingError.GuaranteeReferenceNotFound))(any(), any())
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

      val mockAuditService = mock[AuditService]
      when(
        mockAuditService.balanceRequestFailed(eqTo((RoutingError.InvalidAccessCode)))(any(), any())
      ).thenReturn(IO(()))

      val sut = new GuaranteeBalanceController(
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
        "message" -> "Guarantee balance not found."
      )

      verify(mockAuditService, times(1))
        .balanceRequestFailed(eqTo(RoutingError.InvalidAccessCode))(any(), any())
  }

}
