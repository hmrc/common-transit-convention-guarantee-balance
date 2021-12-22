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

package connectors

import cats.effect.unsafe.implicits.global
import com.github.tomakehurst.wiremock.client.WireMock._
import models.backend.BalanceRequestSuccess
import models.backend.PendingBalanceRequest
import models.request.BalanceRequest
import models.values.AccessCode
import models.values.BalanceId
import models.values.CurrencyCode
import models.values.GuaranteeReference
import models.values.TaxIdentifier
import org.scalatest.EitherValues
import org.scalatest.Inside
import org.scalatest.OptionValues
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.RequestId
import uk.gov.hmrc.http.UpstreamErrorResponse._
import uk.gov.hmrc.http.{HeaderNames => HmrcHeaderNames}

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class BalanceRequestConnectorSpec
  extends AsyncFlatSpec
  with Matchers
  with EitherValues
  with OptionValues
  with Inside
  with WireMockSpec {

  override def portConfigKeys = Seq(
    "microservice.services.transit-movements-guarantee-balance.port"
  )

  implicit val hc = HeaderCarrier(
    requestId = Some(RequestId("6790293d-a688-4d85-9cd4-c8e24e163179"))
  )

  val requestJson = Json.obj(
    "taxIdentifier"      -> "GB12345678900",
    "guaranteeReference" -> "05DE3300BE0001067A001017",
    "accessCode"         -> "1234"
  )
  val request = BalanceRequest(
    TaxIdentifier("GB12345678900"),
    GuaranteeReference("05DE3300BE0001067A001017"),
    AccessCode("1234")
  )

  "BalanceRequestConnector.sendRequest" should "return sync response when downstream component returns OK" in {
    val connector = injector.instanceOf[BalanceRequestConnector]

    wireMockServer.stubFor(
      post(urlEqualTo("/transit-movements-guarantee-balance/balances"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withHeader(HmrcHeaderNames.xRequestId, equalTo("6790293d-a688-4d85-9cd4-c8e24e163179"))
        .withHeader("Channel", equalTo("api"))
        .withRequestBody(equalToJson(Json.stringify(requestJson)))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              Json.stringify(
                Json.obj(
                  "status"   -> "SUCCESS",
                  "balance"  -> 12345678.9,
                  "currency" -> "GBP"
                )
              )
            )
        )
    )

    connector
      .sendRequest(request)
      .map { response =>
        response shouldBe a[Right[_, _]]
        response.value shouldBe Right(
          BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP"))
        )
      }
      .unsafeToFuture()
  }

  it should "return async response when downstream component returns ACCEPTED" in {
    val connector = injector.instanceOf[BalanceRequestConnector]

    wireMockServer.stubFor(
      post(urlEqualTo("/transit-movements-guarantee-balance/balances"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withHeader("Channel", equalTo("api"))
        .withHeader(HmrcHeaderNames.xRequestId, equalTo("6790293d-a688-4d85-9cd4-c8e24e163179"))
        .withRequestBody(equalToJson(Json.stringify(requestJson)))
        .willReturn(
          aResponse()
            .withStatus(ACCEPTED)
            .withBody(""""22b9899e-24ee-48e6-a189-97d1f45391c4"""")
        )
    )

    connector
      .sendRequest(request)
      .map { response =>
        response shouldBe a[Right[_, _]]
        response.value shouldBe Left(
          BalanceId(UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4"))
        )
      }
      .unsafeToFuture()
  }

  it should "return an error response if the downstream component returns a client error" in {
    val connector = injector.instanceOf[BalanceRequestConnector]

    wireMockServer.stubFor(
      post(urlEqualTo("/transit-movements-guarantee-balance/balances"))
        .willReturn(aResponse().withStatus(FORBIDDEN))
    )

    connector
      .sendRequest(request)
      .map { response =>
        response shouldBe a[Left[_, _]]
        inside(response.left.value) { case Upstream4xxResponse(response) =>
          response.statusCode shouldBe FORBIDDEN
        }
      }
      .unsafeToFuture()
  }

  it should "return an error response if the downstream component returns a server error" in {
    val connector = injector.instanceOf[BalanceRequestConnector]

    wireMockServer.stubFor(
      post(urlEqualTo("/transit-movements-guarantee-balance/balances"))
        .willReturn(aResponse().withStatus(BAD_GATEWAY))
    )

    connector
      .sendRequest(request)
      .map { response =>
        response shouldBe a[Left[_, _]]
        inside(response.left.value) { case Upstream5xxResponse(response) =>
          response.statusCode shouldBe BAD_GATEWAY
        }
      }
      .unsafeToFuture()
  }

  "BalanceRequestConnector.getRequest" should "return pending request when the downstream component returns OK" in {
    val connector = injector.instanceOf[BalanceRequestConnector]

    val balanceId = BalanceId(UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4"))

    wireMockServer.stubFor(
      get(urlEqualTo(s"/transit-movements-guarantee-balance/balances/${balanceId.value}"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withHeader(HmrcHeaderNames.xRequestId, equalTo("6790293d-a688-4d85-9cd4-c8e24e163179"))
        .withHeader("Channel", equalTo("api"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              Json.stringify(
                Json.obj(
                  "balanceId"          -> "22b9899e-24ee-48e6-a189-97d1f45391c4",
                  "taxIdentifier"      -> "GB12345678900",
                  "guaranteeReference" -> "05DE3300BE0001067A001017",
                  "requestedAt"        -> "2021-09-14T09:52:15Z",
                  "completedAt"        -> "2021-09-14T09:53:05Z",
                  "response" -> Json.obj(
                    "status"   -> "SUCCESS",
                    "balance"  -> 12345678.9,
                    "currency" -> "GBP"
                  )
                )
              )
            )
        )
    )

    connector
      .getRequest(balanceId)
      .map { response =>
        response shouldBe a[Some[_]]
        response.value shouldBe Right(
          PendingBalanceRequest(
            balanceId,
            TaxIdentifier("GB12345678900"),
            GuaranteeReference("05DE3300BE0001067A001017"),
            OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 52, 15), ZoneOffset.UTC).toInstant,
            completedAt = Some(
              OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 53, 5), ZoneOffset.UTC).toInstant
            ),
            response = Some(BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP")))
          )
        )
      }
      .unsafeToFuture()
  }

  it should "return None if the downstream component returns a not found error" in {
    val connector = injector.instanceOf[BalanceRequestConnector]

    val balanceId = BalanceId(UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4"))

    wireMockServer.stubFor(
      get(urlEqualTo(s"/transit-movements-guarantee-balance/balances/${balanceId.value}"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withHeader(HmrcHeaderNames.xRequestId, equalTo("6790293d-a688-4d85-9cd4-c8e24e163179"))
        .withHeader("Channel", equalTo("api"))
        .willReturn(aResponse().withStatus(NOT_FOUND))
    )

    connector
      .getRequest(balanceId)
      .map { response =>
        response shouldBe None
      }
      .unsafeToFuture()
  }

  it should "return the error wrapped in Left if the downstream component returns a different client error" in {
    val connector = injector.instanceOf[BalanceRequestConnector]

    val balanceId = BalanceId(UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4"))

    wireMockServer.stubFor(
      get(urlEqualTo(s"/transit-movements-guarantee-balance/balances/${balanceId.value}"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withHeader(HmrcHeaderNames.xRequestId, equalTo("6790293d-a688-4d85-9cd4-c8e24e163179"))
        .withHeader("Channel", equalTo("api"))
        .willReturn(aResponse().withStatus(METHOD_NOT_ALLOWED))
    )

    connector
      .getRequest(balanceId)
      .map { response =>
        response shouldBe a[Some[_]]
        response.value shouldBe a[Left[_, _]]
        response.value.left.value.statusCode shouldBe METHOD_NOT_ALLOWED
      }
      .unsafeToFuture()
  }

  it should "return the error wrapped in Left if the downstream component returns a server error" in {
    val connector = injector.instanceOf[BalanceRequestConnector]

    val balanceId = BalanceId(UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4"))

    wireMockServer.stubFor(
      get(urlEqualTo(s"/transit-movements-guarantee-balance/balances/${balanceId.value}"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withHeader(HmrcHeaderNames.xRequestId, equalTo("6790293d-a688-4d85-9cd4-c8e24e163179"))
        .withHeader("Channel", equalTo("api"))
        .willReturn(aResponse().withStatus(GATEWAY_TIMEOUT))
    )

    connector
      .getRequest(balanceId)
      .map { response =>
        response shouldBe a[Some[_]]
        response.value shouldBe a[Left[_, _]]
        response.value.left.value.statusCode shouldBe GATEWAY_TIMEOUT
      }
      .unsafeToFuture()
  }
}
