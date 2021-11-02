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
import models.backend.BalanceRequestXmlError
import models.backend.PendingBalanceRequest
import models.backend.errors.FunctionalError
import models.backend.errors.XmlError
import models.request.BalanceRequest
import models.response.PostBalanceRequestFunctionalErrorResponse
import models.response._
import models.values._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.i18n.DefaultMessagesApi
import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._
import services.BalanceRequestService
import services.BalanceRequestValidationService
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
      new BalanceRequestValidationService,
      Helpers.stubControllerComponents(
        messagesApi = new DefaultMessagesApi(
          Map(
            "en" -> Map(
              "error.expected.date"               -> "Date value expected",
              "error.expected.date.isoformat"     -> "Iso date value expected",
              "error.expected.time"               -> "Time value expected",
              "error.expected.jsarray"            -> "Array value expected",
              "error.expected.jsboolean"          -> "Boolean value expected",
              "error.expected.jsnumber"           -> "Number value expected",
              "error.expected.jsobject"           -> "Object value expected",
              "error.expected.jsstring"           -> "String value expected",
              "error.expected.jsnumberorjsstring" -> "String or number expected",
              "error.expected.keypathnode"        -> "Node value expected",
              "error.expected.uuid"               -> "UUID value expected",
              "error.expected.validenumvalue"     -> "Valid enumeration value expected",
              "error.expected.enumstring"         -> "String value expected",
              "error.path.empty"                  -> "Empty path",
              "error.path.missing"                -> "Missing path",
              "error.path.result.multiple"        -> "Multiple results for the given path"
            )
          )
        )
      ),
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
      .withBody(Json.toJson(balanceRequest))
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
      .withBody(Json.toJson(balanceRequest))
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

    val request = FakeRequest().withBody(Json.toJson(balanceRequest))

    val result = controller().submitBalanceRequest(request)

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
      .withBody(Json.toJson(balanceRequest))
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
      .withBody(Json.toJson(balanceRequest))
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

  it should "return 400 when the request JSON is not a valid balance request" in {
    val missingAccessCodeRequest = FakeRequest()
      .withBody(
        Json.obj(
          "taxIdentifier"      -> "GB12345678900",
          "guaranteeReference" -> "05DE3300BE0001067A001017"
        )
      )
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val missingAccessCodeResult = controller().submitBalanceRequest(missingAccessCodeRequest)

    status(missingAccessCodeResult) shouldBe BAD_REQUEST
    contentType(missingAccessCodeResult) shouldBe Some(ContentTypes.JSON)
    contentAsJson(missingAccessCodeResult) shouldBe Json.obj(
      "code"    -> "INVALID_REQUEST_JSON",
      "message" -> "Invalid request JSON",
      "errors" -> Json.obj(
        "$.accessCode" -> Json.arr("Missing path")
      )
    )

    val emptyObjectRequest = FakeRequest()
      .withBody(Json.obj())
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val emptyObjectResult = controller().submitBalanceRequest(emptyObjectRequest)

    status(emptyObjectResult) shouldBe BAD_REQUEST
    contentType(emptyObjectResult) shouldBe Some(ContentTypes.JSON)
    contentAsJson(emptyObjectResult) shouldBe Json.obj(
      "code"    -> "INVALID_REQUEST_JSON",
      "message" -> "Invalid request JSON",
      "errors" -> Json.obj(
        "$.taxIdentifier"      -> Json.arr("Missing path"),
        "$.guaranteeReference" -> Json.arr("Missing path"),
        "$.accessCode"         -> Json.arr("Missing path")
      )
    )

    val wrongAccessCodeTypeRequest = FakeRequest()
      .withBody(
        Json.obj(
          "taxIdentifier"      -> "GB12345678900",
          "guaranteeReference" -> "05DE3300BE0001067A001017",
          "accessCode"         -> 1234
        )
      )
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val wrongAccessCodeTypeResult = controller().submitBalanceRequest(wrongAccessCodeTypeRequest)

    status(wrongAccessCodeTypeResult) shouldBe BAD_REQUEST
    contentType(wrongAccessCodeTypeResult) shouldBe Some(ContentTypes.JSON)
    contentAsJson(wrongAccessCodeTypeResult) shouldBe Json.obj(
      "code"    -> "INVALID_REQUEST_JSON",
      "message" -> "Invalid request JSON",
      "errors" -> Json.obj(
        "$.accessCode" -> Json.arr("String value expected")
      )
    )

    val wrongRootElementRequest = FakeRequest()
      .withBody(JsString(""))
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val wrongRootElementResult = controller().submitBalanceRequest(wrongRootElementRequest)

    status(wrongRootElementResult) shouldBe BAD_REQUEST
    contentType(wrongRootElementResult) shouldBe Some(ContentTypes.JSON)
    contentAsJson(wrongRootElementResult) shouldBe Json.obj(
      "code"    -> "INVALID_REQUEST_JSON",
      "message" -> "Invalid request JSON",
      "errors" -> Json.obj(
        "$.taxIdentifier"      -> Json.arr("Missing path"),
        "$.guaranteeReference" -> Json.arr("Missing path"),
        "$.accessCode"         -> Json.arr("Missing path")
      )
    )
  }

  it should "return 400 when the request contains a tax identifier that is too long" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB123456789001920319203"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val request = FakeRequest()
      .withBody(Json.toJson(balanceRequest))
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller().submitBalanceRequest(request)

    status(result) shouldBe BAD_REQUEST
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "Bad request",
      "errors" -> Json.arr(
        Json.obj(
          "code"    -> "INVALID_TAX_IDENTIFIER",
          "message" -> "Invalid tax identifier value",
          "reason"  -> "Tax identifier has a maximum length of 17 characters"
        )
      )
    )
  }

  it should "return 400 when the request contains a tax identifier which includes invalid characters" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB123456789001920319203####"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val request = FakeRequest()
      .withBody(Json.toJson(balanceRequest))
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller().submitBalanceRequest(request)

    status(result) shouldBe BAD_REQUEST
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "Bad request",
      "errors" -> Json.arr(
        Json.obj(
          "code"    -> "INVALID_TAX_IDENTIFIER",
          "message" -> "Invalid tax identifier value",
          "reason"  -> "Tax identifier has a maximum length of 17 characters"
        ),
        Json.obj(
          "code"    -> "INVALID_TAX_IDENTIFIER",
          "message" -> "Invalid tax identifier value",
          "reason"  -> "Tax identifier must be alphanumeric"
        )
      )
    )
  }

  it should "return 400 when the request contains a guarantee reference that is too long" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB123456789001"),
      GuaranteeReference("05DE3300BE0001067A00101723232"),
      AccessCode("1234")
    )

    val request = FakeRequest()
      .withBody(Json.toJson(balanceRequest))
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller().submitBalanceRequest(request)

    status(result) shouldBe BAD_REQUEST
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "Bad request",
      "errors" -> Json.arr(
        Json.obj(
          "code"    -> "INVALID_GUARANTEE_REFERENCE",
          "message" -> "Invalid guarantee reference value",
          "reason"  -> "Guarantee reference has a maximum length of 24 characters"
        )
      )
    )
  }

  it should "return 400 when the request contains a guarantee reference that is too short" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB123456789001"),
      GuaranteeReference("05DE3300BE000106"),
      AccessCode("1234")
    )

    val request = FakeRequest()
      .withBody(Json.toJson(balanceRequest))
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller().submitBalanceRequest(request)

    status(result) shouldBe BAD_REQUEST
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "Bad request",
      "errors" -> Json.arr(
        Json.obj(
          "code"    -> "INVALID_GUARANTEE_REFERENCE",
          "message" -> "Invalid guarantee reference value",
          "reason"  -> "Guarantee reference has a minimum length of 17 characters"
        )
      )
    )
  }

  it should "return 400 when the request contains a guarantee reference which includes invalid characters" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB123456789001"),
      GuaranteeReference("05DE3300B@€€01067A00101712931"),
      AccessCode("1234")
    )

    val request = FakeRequest()
      .withBody(Json.toJson(balanceRequest))
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller().submitBalanceRequest(request)

    status(result) shouldBe BAD_REQUEST
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "Bad request",
      "errors" -> Json.arr(
        Json.obj(
          "code"    -> "INVALID_GUARANTEE_REFERENCE",
          "message" -> "Invalid guarantee reference value",
          "reason"  -> "Guarantee reference has a maximum length of 24 characters"
        ),
        Json.obj(
          "code"    -> "INVALID_GUARANTEE_REFERENCE",
          "message" -> "Invalid guarantee reference value",
          "reason"  -> "Guarantee reference must be alphanumeric"
        )
      )
    )
  }

  it should "return 400 when the request contains an access code that is too long" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB123456789001"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("12345")
    )

    val request = FakeRequest()
      .withBody(Json.toJson(balanceRequest))
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller().submitBalanceRequest(request)

    status(result) shouldBe BAD_REQUEST
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "Bad request",
      "errors" -> Json.arr(
        Json.obj(
          "code"    -> "INVALID_ACCESS_CODE",
          "message" -> "Invalid access code value",
          "reason"  -> "Access code must be 4 characters in length"
        )
      )
    )
  }

  it should "return 400 when the request contains an access code that is too short" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB123456789001"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("123")
    )

    val request = FakeRequest()
      .withBody(Json.toJson(balanceRequest))
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller().submitBalanceRequest(request)

    status(result) shouldBe BAD_REQUEST
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "Bad request",
      "errors" -> Json.arr(
        Json.obj(
          "code"    -> "INVALID_ACCESS_CODE",
          "message" -> "Invalid access code value",
          "reason"  -> "Access code must be 4 characters in length"
        )
      )
    )
  }

  it should "return 400 when the request contains an access code which includes invalid characters" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB123456789001"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234#£@$#")
    )

    val request = FakeRequest()
      .withBody(Json.toJson(balanceRequest))
      .withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller().submitBalanceRequest(request)

    status(result) shouldBe BAD_REQUEST
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "Bad request",
      "errors" -> Json.arr(
        Json.obj(
          "code"    -> "INVALID_ACCESS_CODE",
          "message" -> "Invalid access code value",
          "reason"  -> "Access code must be 4 characters in length"
        ),
        Json.obj(
          "code"    -> "INVALID_ACCESS_CODE",
          "message" -> "Invalid access code value",
          "reason"  -> "Access code must be alphanumeric"
        )
      )
    )
  }

  it should "return 500 when there is an upstream service error" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val request = FakeRequest()
      .withBody(Json.toJson(balanceRequest))
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
      .withBody(Json.toJson(balanceRequest))
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
      .withBody(Json.toJson(balanceRequest))
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

  it should "return 500 when the balance request response is an XML error" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val balanceRequestXmlError =
      BalanceRequestXmlError(
        NonEmptyList.one(XmlError(ErrorType(14), "Foo.Bar(1).Baz", None))
      )

    val pendingBalanceRequest = PendingBalanceRequest(
      balanceId,
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 52, 15), ZoneOffset.UTC).toInstant,
      completedAt = Some(
        OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 53, 5), ZoneOffset.UTC).toInstant
      ),
      response = Some(balanceRequestXmlError)
    )

    val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> "application/vnd.hmrc.1.0+json")

    val result = controller(
      getRequestResponse = IO.some(Right(pendingBalanceRequest))
    ).getBalanceRequest(balanceId)(request)

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
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

    val result = controller().getBalanceRequest(balanceId)(FakeRequest())

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
