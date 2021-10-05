/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import config.AppConfig
import connectors.FakeBalanceRequestConnector
import controllers.actions.FakeAuthActionProvider
import metrics.FakeMetrics
import models.backend.BalanceRequestFunctionalError
import models.backend.BalanceRequestResponse
import models.backend.BalanceRequestSuccess
import models.backend.PendingBalanceRequest
import models.backend.errors.FunctionalError
import models.request.BalanceRequest
import models.response.PostBalanceRequestFunctionalErrorResponse
import models.response._
import models.values._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._
import services.BalanceRequestService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class BalanceRequestControllerSpec extends AnyFlatSpec with Matchers {

  def mkAppConfig(config: Configuration) = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  def controller(
    getRequestResponse: IO[Option[Either[UpstreamErrorResponse, PendingBalanceRequest]]] = IO.stub,
    sendRequestResponse: IO[
      Either[UpstreamErrorResponse, Either[BalanceId, BalanceRequestResponse]]
    ] = IO.stub,
    appConfig: AppConfig = mkAppConfig(Configuration())
  ) = {
    val service = new BalanceRequestService(
      FakeBalanceRequestConnector(
        getRequestResponse = getRequestResponse,
        sendRequestResponse = sendRequestResponse
      )
    )

    new BalanceRequestController(
      appConfig,
      FakeAuthActionProvider,
      service,
      Helpers.stubControllerComponents(),
      IORuntime.global,
      new FakeMetrics
    )
  }

  "BalanceRequestController.submitBalanceRequest" should "return 200 when there is a successful sync response" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val balanceRequestSuccess =
      BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP"))

    val request = FakeRequest()
      .withBody(balanceRequest)
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      sendRequestResponse = IO(Right(Right(balanceRequestSuccess)))
    ).submitBalanceRequest(request)

    status(result) shouldBe OK
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.toJson(
      PostBalanceRequestSuccessResponse(balanceRequestSuccess)
    )
  }

  it should "return 400 when there is a functional error sync response" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val balanceRequestFunctionalError =
      BalanceRequestFunctionalError(
        NonEmptyList.one(FunctionalError(ErrorType(14), "Foo.Bar(1).Baz", None))
      )

    val request = FakeRequest()
      .withBody(balanceRequest)
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      sendRequestResponse = IO(Right(Right(balanceRequestFunctionalError)))
    ).submitBalanceRequest(request)

    status(result) shouldBe BAD_REQUEST
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.toJson(
      PostBalanceRequestFunctionalErrorResponse(balanceRequestFunctionalError)
    )
  }

  it should "return 406 when the accept header is missing" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val balanceRequestSuccess =
      BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP"))

    val request = FakeRequest().withBody(balanceRequest)

    val result = controller(
      sendRequestResponse = IO(Right(Right(balanceRequestSuccess)))
    ).submitBalanceRequest(request)

    status(result) shouldBe NOT_ACCEPTABLE
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "ACCEPT_HEADER_INVALID",
      "message" -> "The accept header is missing or invalid"
    )
  }

  it should "return 504 when the upstream service times out and async response feature is disabled" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val balanceId = BalanceId(UUID.randomUUID())

    val request = FakeRequest()
      .withBody(balanceRequest)
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      sendRequestResponse = IO(Right(Left(balanceId))),
      appConfig = mkAppConfig(Configuration("features.async-balance-response" -> "false"))
    ).submitBalanceRequest(request)

    status(result) shouldBe GATEWAY_TIMEOUT
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "GATEWAY_TIMEOUT",
      "message" -> "Request timed out"
    )
  }

  it should "return 202 when the upstream service times out and async response feature is enabled" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val balanceId = BalanceId(UUID.randomUUID())

    val request = FakeRequest()
      .withBody(balanceRequest)
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      sendRequestResponse = IO(Right(Left(balanceId))),
      appConfig = mkAppConfig(Configuration("features.async-balance-response" -> "true"))
    ).submitBalanceRequest(request)

    status(result) shouldBe ACCEPTED
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.toJson(PostBalanceRequestPendingResponse(balanceId))
    header(HeaderNames.LOCATION, result) shouldBe Some(
      s"/customs/guarantees/balances/${balanceId.value}"
    )
  }

  it should "return 500 when there is an upstream service error" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val request = FakeRequest()
      .withBody(balanceRequest)
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      sendRequestResponse = IO(Left(UpstreamErrorResponse("Kaboom!!!", 502)))
    ).submitBalanceRequest(request)

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "return 500 when there is a client error when calling the upstream service" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val request = FakeRequest()
      .withBody(balanceRequest)
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      sendRequestResponse = IO(Left(UpstreamErrorResponse("Argh!!!", 400)))
    ).submitBalanceRequest(request)

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "return 500 when there is an unhandled runtime exception" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val request = FakeRequest()
      .withBody(balanceRequest)
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      sendRequestResponse = IO.raiseError(new RuntimeException)
    ).submitBalanceRequest(request)

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  "BalanceRequestController.getBalanceRequest" should "return 200 and the balance request data when everything is successful" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val balanceRequestSuccess =
      BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP"))

    val pendingBalanceRequest = PendingBalanceRequest(
      balanceId,
      EnrolmentId("12345678ABC"),
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 52, 15), ZoneOffset.UTC).toInstant,
      completedAt = Some(
        OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 53, 5), ZoneOffset.UTC).toInstant
      ),
      response = Some(balanceRequestSuccess)
    )

    val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      getRequestResponse = IO.some(Right(pendingBalanceRequest))
    ).getBalanceRequest(balanceId)(request)

    status(result) shouldBe OK
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.toJson(
      GetBalanceRequestResponse(balanceId, pendingBalanceRequest)
    )
  }

  it should "return 404 when the balance request is not found" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      getRequestResponse = IO.none
    ).getBalanceRequest(balanceId)(request)

    status(result) shouldBe NOT_FOUND
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "NOT_FOUND",
      "message" -> "The balance request with ID 22b9899e-24ee-48e6-a189-97d1f45391c4 was not found"
    )
  }

  it should "return 406 when the accept header is missing" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val balanceRequestSuccess =
      BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP"))

    val pendingBalanceRequest = PendingBalanceRequest(
      balanceId,
      EnrolmentId("12345678ABC"),
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 52, 15), ZoneOffset.UTC).toInstant,
      completedAt = Some(
        OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 53, 5), ZoneOffset.UTC).toInstant
      ),
      response = Some(balanceRequestSuccess)
    )

    val result = controller(
      getRequestResponse = IO.some(Right(pendingBalanceRequest))
    ).getBalanceRequest(balanceId)(FakeRequest())

    status(result) shouldBe NOT_ACCEPTABLE
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "ACCEPT_HEADER_INVALID",
      "message" -> "The accept header is missing or invalid"
    )
  }

  it should "return 500 when there is an upstream client error" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      getRequestResponse = IO.some(Left(UpstreamErrorResponse("Argh!!!", CONFLICT)))
    ).getBalanceRequest(balanceId)(request)

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "return 500 when there is an upstream server error" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      getRequestResponse = IO.some(Left(UpstreamErrorResponse("Kaboom!!!", BAD_GATEWAY)))
    ).getBalanceRequest(balanceId)(request)

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "return 500 when there is an unhandled runtime exception" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      getRequestResponse = IO.raiseError(new RuntimeException)
    ).getBalanceRequest(balanceId)(request)

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }
}
