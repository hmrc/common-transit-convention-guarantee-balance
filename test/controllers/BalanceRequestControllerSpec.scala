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
import connectors.FakeBalanceRequestConnector
import controllers.actions.FakeAuthActionProvider
import models.backend.BalanceRequestFunctionalError
import models.backend.BalanceRequestResponse
import models.backend.BalanceRequestSuccess
import models.backend.errors.FunctionalError
import models.request.BalanceRequest
import models.response.PostBalanceRequestFunctionalErrorResponse
import models.response._
import models.values._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._
import services.BalanceRequestService
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.util.UUID

class BalanceRequestControllerSpec extends AnyFlatSpec with Matchers {

  def controller(
    sendRequestResponse: IO[
      Either[UpstreamErrorResponse, Either[BalanceId, BalanceRequestResponse]]
    ] = IO.stub
  ) = {
    val service = new BalanceRequestService(
      FakeBalanceRequestConnector(
        sendRequestResponse = sendRequestResponse
      )
    )

    new BalanceRequestController(
      FakeAuthActionProvider,
      service,
      Helpers.stubControllerComponents(),
      IORuntime.global
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

    val result = controller(
      sendRequestResponse = IO(Right(Right(balanceRequestSuccess)))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

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

    val result = controller(
      sendRequestResponse = IO(Right(Right(balanceRequestFunctionalError)))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe BAD_REQUEST
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.toJson(
      PostBalanceRequestFunctionalErrorResponse(balanceRequestFunctionalError)
    )
  }

  it should "return 202 when there is a valid async response" in {
    val balanceRequest = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    val balanceId = BalanceId(UUID.randomUUID())

    val result = controller(
      sendRequestResponse = IO(Right(Left(balanceId)))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

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

    val result = controller(
      sendRequestResponse = IO(Left(UpstreamErrorResponse("Kaboom!!!", 502)))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

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

    val result = controller(
      sendRequestResponse = IO(Left(UpstreamErrorResponse("Argh!!!", 400)))
    ).submitBalanceRequest(FakeRequest().withBody(balanceRequest))

    status(result) shouldBe INTERNAL_SERVER_ERROR
    contentType(result) shouldBe Some(ContentTypes.JSON)
    contentAsJson(result) shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  "BalanceRequestController.getBalanceRequest" should "return 404 when the balance request is not found" in {
    val result = controller().getBalanceRequest(BalanceId(UUID.randomUUID()))(FakeRequest())

    status(result) shouldBe NOT_FOUND
  }
}
