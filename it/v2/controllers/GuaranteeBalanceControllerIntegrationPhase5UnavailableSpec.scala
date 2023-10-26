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

import akka.stream.scaladsl.Sink
import com.github.tomakehurst.wiremock.client.WireMock._
import com.kenshoo.play.metrics.Metrics
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
import play.api.http._
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.test._
import uk.gov.hmrc.http.HeaderCarrier
import v2.models._
import v2.util._

/** These tests test the full journey from request to response, via all parts of the system.
  * Note that the authentication isn't done as part of this, so checking the internal IDs is not provided for here.
  */
class GuaranteeBalanceControllerIntegrationPhase5UnavailableSpec
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
    bind[Metrics].toInstance(new FakeMetrics),
    bind[AuthActionProvider].toInstance(FakeIntegrationAuthActionProvider)
  )

  override def configuration: Seq[(String, Any)] = Seq("enable-phase-5" -> false)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "GuaranteeBalanceController#postRequest" should "return NOT_ACCEPTABLE if Phase 5 is unavailable" in {
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
                  "code"    -> "NOT_ACCEPTABLE",
                  "message" -> "CTC Guarantee Balance API version 2 is not yet available. Please continue to use version 1 to submit transit messages."
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
      "code"    -> "NOT_ACCEPTABLE",
      "message" -> "CTC Guarantee Balance API version 2 is not yet available. Please continue to use version 1 to submit transit messages."
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

}
