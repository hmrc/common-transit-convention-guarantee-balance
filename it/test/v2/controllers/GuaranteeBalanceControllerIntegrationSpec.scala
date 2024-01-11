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

import org.apache.pekko.stream.scaladsl.Sink
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import connectors.WireMockSpec
import controllers.actions.AuthActionProvider
import fakes.controllers.actions.FakeIntegrationAuthActionProvider
import fakes.metrics.FakeMetrics
import models.request.AuthenticatedRequest
import models.values.InternalId
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.FORBIDDEN
import play.api.http.Status.NOT_ACCEPTABLE
import play.api.http.Status.NOT_FOUND
import play.api.http.Status.OK
import play.api.http.Status.TOO_MANY_REQUESTS
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import v2.models.Balance
import v2.models.GuaranteeReferenceNumber
import v2.util.Generators
import v2.util.TestActorSystem

/** These tests test the full journey from request to response, via all parts of the system.
  * Note that the authentication isn't done as part of this, so checking the internal IDs is not provided for here.
  */
class GuaranteeBalanceControllerIntegrationSpec
    extends AsyncFlatSpec
    with Matchers
    with ScalaFutures
    with Inside
    with WireMockSpec
    with Generators
    with TestActorSystem {

  override def portConfigKeys: Seq[String] = Seq(
    "microservice.services.ctc-guarantee-balance-router.port"
  )

  override protected val bindings: Seq[GuiceableModule] = Seq(
    bind[com.codahale.metrics.MetricRegistry].toInstance(new FakeMetrics),
    bind[AuthActionProvider].toInstance(FakeIntegrationAuthActionProvider)
  )

  override def configuration: Seq[(String, Any)] = Seq("enable-phase-5" -> true)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "GuaranteeBalanceController#postRequest" should "return OK with a result if everything is okay" in {
    val grn        = arbitrary[GuaranteeReferenceNumber].sample.get
    val internalId = arbitrary[InternalId].sample.get
    val amount     = arbitrary[Balance].sample.get

    wireMockServer.stubFor(
      post(urlEqualTo(s"/ctc-guarantee-balance-router/${grn.value}/balance"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withRequestBody(equalToJson(Json.stringify(Json.obj("accessCode" -> "ABCD"))))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              Json.stringify(
                Json.obj(
                  "grn"        -> grn.value,
                  "balance"    -> amount.value,
                  "currencyCL" -> "GBP"
                )
              )
            )
        )
    )

    val request = AuthenticatedRequest(
      FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode" -> "ABCD"
        )
      ),
      internalId
    )

    val expected = Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj(
          "href" -> s"/customs/guarantees/${grn.value}/balance"
        )
      ),
      "balance"  -> amount.value,
      "currency" -> "GBP"
    )

    val sut = injector.instanceOf[GuaranteeBalanceController]
    sut
      .postRequest(grn)(request)
      .flatMap {
        result =>
          result.header.status shouldBe OK
          result.body.dataStream.reduce(_ ++ _).map(_.utf8String).runWith(Sink.head[String])
      }
      .map {
        result =>
          Json.parse(result) shouldBe expected
      }

  }

  it should "return not found if the GRN couldn't be found" in {
    val grn        = arbitrary[GuaranteeReferenceNumber].sample.get
    val internalId = arbitrary[InternalId].sample.get

    wireMockServer.stubFor(
      post(urlEqualTo(s"/ctc-guarantee-balance-router/${grn.value}/balance"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withRequestBody(equalToJson(Json.stringify(Json.obj("accessCode" -> "ABCD"))))
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)
            .withBody(
              Json.stringify(
                Json.obj(
                  "code"    -> "NOT_FOUND",
                  "message" -> "not found"
                )
              )
            )
        )
    )

    val request = AuthenticatedRequest(
      FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode" -> "ABCD"
        )
      ),
      internalId
    )

    val expected = Json.obj(
      "code"    -> "NOT_FOUND",
      "message" -> "The guarantee reference number or access code did not match an existing guarantee."
    )

    val sut = injector.instanceOf[GuaranteeBalanceController]
    sut
      .postRequest(grn)(request)
      .flatMap {
        result =>
          result.header.status shouldBe NOT_FOUND
          result.body.dataStream.reduce(_ ++ _).map(_.utf8String).runWith(Sink.head[String])
      }
      .map {
        result =>
          Json.parse(result) shouldBe expected
      }

  }

  it should "return not found if the access code is incorrect" in {
    val grn        = arbitrary[GuaranteeReferenceNumber].sample.get
    val internalId = arbitrary[InternalId].sample.get

    wireMockServer.stubFor(
      post(urlEqualTo(s"/ctc-guarantee-balance-router/${grn.value}/balance"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withRequestBody(equalToJson(Json.stringify(Json.obj("accessCode" -> "ABCD"))))
        .willReturn(
          aResponse()
            .withStatus(FORBIDDEN)
            .withBody(
              Json.stringify(
                Json.obj(
                  "code"    -> "FORBIDDEN",
                  "message" -> "forbidden"
                )
              )
            )
        )
    )

    val request = AuthenticatedRequest(
      FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode" -> "ABCD"
        )
      ),
      internalId
    )

    val expected = Json.obj(
      "code"    -> "NOT_FOUND",
      "message" -> "The guarantee reference number or access code did not match an existing guarantee."
    )

    val sut = injector.instanceOf[GuaranteeBalanceController]
    sut
      .postRequest(grn)(request)
      .flatMap {
        result =>
          result.header.status shouldBe NOT_FOUND
          result.body.dataStream.reduce(_ ++ _).map(_.utf8String).runWith(Sink.head[String])
      }
      .map {
        result =>
          Json.parse(result) shouldBe expected
      }

  }

  it should "return bad request if the guarantee type is invalid" in {
    val grn        = arbitrary[GuaranteeReferenceNumber].sample.get
    val internalId = arbitrary[InternalId].sample.get

    wireMockServer.stubFor(
      post(urlEqualTo(s"/ctc-guarantee-balance-router/${grn.value}/balance"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withRequestBody(equalToJson(Json.stringify(Json.obj("accessCode" -> "ABCD"))))
        .willReturn(
          aResponse()
            .withStatus(BAD_REQUEST)
            .withBody(
              Json.stringify(
                Json.obj(
                  "code"    -> "INVALID_GUARANTEE_TYPE",
                  "message" -> "Guarantee type is not supported."
                )
              )
            )
        )
    )

    val request = AuthenticatedRequest(
      FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode" -> "ABCD"
        )
      ),
      internalId
    )

    val expected = Json.obj(
      "code"    -> "INVALID_GUARANTEE_TYPE",
      "message" -> "Guarantee type is not supported."
    )

    val sut = injector.instanceOf[GuaranteeBalanceController]
    sut
      .postRequest(grn)(request)
      .flatMap {
        result =>
          result.header.status shouldBe BAD_REQUEST
          result.body.dataStream.reduce(_ ++ _).map(_.utf8String).runWith(Sink.head[String])
      }
      .map {
        result =>
          Json.parse(result) shouldBe expected
      }

  }

  it should "return not acceptable if the accept header is incorrect" in {
    val grn        = arbitrary[GuaranteeReferenceNumber].sample.get
    val internalId = arbitrary[InternalId].sample.get

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
          "accessCode" -> "ABCD"
        )
      ),
      internalId
    )

    val expected = Json.obj(
      "code"    -> "NOT_ACCEPTABLE",
      "message" -> "The accept header must be set to application/vnd.hmrc.2.0+json to use this resource."
    )

    val sut = injector.instanceOf[GuaranteeBalanceController]
    sut
      .postRequest(grn)(request)
      .flatMap {
        result =>
          result.header.status shouldBe NOT_ACCEPTABLE
          result.body.dataStream.reduce(_ ++ _).map(_.utf8String).runWith(Sink.head[String])
      }
      .map {
        result =>
          Json.parse(result) shouldBe expected
      }

  }

  it should "return bad request if the Json does not contain an access code" in {
    val grn        = arbitrary[GuaranteeReferenceNumber].sample.get
    val internalId = arbitrary[InternalId].sample.get

    val request = AuthenticatedRequest(
      FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "nope" -> "ABCD"
        )
      ),
      internalId
    )

    val expected = Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "The access code was not supplied."
    )

    val sut = injector.instanceOf[GuaranteeBalanceController]
    sut
      .postRequest(grn)(request)
      .flatMap {
        result =>
          result.header.status shouldBe BAD_REQUEST
          result.body.dataStream.reduce(_ ++ _).map(_.utf8String).runWith(Sink.head[String])
      }
      .map {
        result =>
          Json.parse(result) shouldBe expected
      }
  }

  Seq("ABCDE", "ABC?", "!A").foreach {
    accessCode =>
      it should s"return bad request if the access code $accessCode is invalid" in {
        val grn        = arbitrary[GuaranteeReferenceNumber].sample.get
        val internalId = arbitrary[InternalId].sample.get

        val request = AuthenticatedRequest(
          FakeRequest(
            "POST",
            "/",
            FakeHeaders(
              Seq(
                HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
              )
            ),
            Json.obj(
              "accessCode" -> accessCode
            )
          ),
          internalId
        )

        val expected = Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> s"Access code $accessCode must be four alphanumeric characters."
        )

        val sut = injector.instanceOf[GuaranteeBalanceController]
        sut
          .postRequest(grn)(request)
          .flatMap {
            result =>
              result.header.status shouldBe BAD_REQUEST
              result.body.dataStream.reduce(_ ++ _).map(_.utf8String).runWith(Sink.head[String])
          }
          .map {
            result =>
              Json.parse(result) shouldBe expected
          }
      }
  }

  it should "return Too Many Requests if two requests are made in quick succession" in {
    val grn        = arbitrary[GuaranteeReferenceNumber].sample.get
    val internalId = arbitrary[InternalId].sample.get

    val request = AuthenticatedRequest(
      FakeRequest(
        "POST",
        "/",
        FakeHeaders(
          Seq(
            HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
          )
        ),
        Json.obj(
          "accessCode" -> "ABCD"
        )
      ),
      internalId
    )

    val expected = Json.obj(
      "code"    -> "MESSAGE_THROTTLED_OUT",
      "message" -> "The request for the API is throttled as you have exceeded your quota."
    )

    val sut = injector.instanceOf[GuaranteeBalanceController]
    sut
      .postRequest(grn)(request)
      .flatMap {
        // we ignore this first request, we do it again
        _ =>
          sut.postRequest(grn)(request)
      }
      .flatMap {
        result =>
          result.header.status shouldBe TOO_MANY_REQUESTS
          result.body.dataStream.reduce(_ ++ _).map(_.utf8String).runWith(Sink.head[String])
      }
      .map {
        result =>
          Json.parse(result) shouldBe expected
      }

  }

}
