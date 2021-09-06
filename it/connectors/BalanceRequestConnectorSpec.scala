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
import models.request.BalanceRequest
import models.values.AccessCode
import models.values.BalanceId
import models.values.CurrencyCode
import models.values.GuaranteeReference
import models.values.TaxIdentifier
import org.scalatest.EitherValues
import org.scalatest.Inside
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse._

import java.util.UUID
import scala.util.Right

class BalanceRequestConnectorSpec
    extends AsyncFlatSpec
    with Matchers
    with EitherValues
    with Inside
    with WireMockSpec {

  override def portConfigKeys = Seq(
    "microservice.services.transit-movements-guarantee-balance.port"
  )

  implicit val hc = HeaderCarrier()

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

  "BalanceRequestConnector" should "return sync response when downstream component returns OK" in {
    val connector = injector.instanceOf[BalanceRequestConnector]

    wireMockServer.stubFor(
      post(urlEqualTo("/transit-movements-guarantee-balance/balances"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
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
}
