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

package v2.connectors

import cats.effect.unsafe.implicits.global
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import connectors.WireMockSpec
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.Status.NOT_FOUND
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.models.AccessCode
import v2.models.Balance
import v2.models.BalanceRequest
import v2.models.GuaranteeReferenceNumber
import v2.models.InternalBalanceResponse
import v2.models.errors.ErrorCode
import v2.util.Generators

import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure

class RouterConnectorSpec
    extends AsyncFlatSpec
    with Matchers
    with ScalaFutures
    with Inside
    with ScalaCheckDrivenPropertyChecks
    with WireMockSpec
    with Generators {

  lazy val dummytoken = Gen.stringOfN(20, Gen.alphaNumChar).sample.get

  override def portConfigKeys: Seq[String] = Seq(
    "microservice.services.ctc-guarantee-balance-router.port"
  )

  override def configuration: Seq[(String, Any)] = Seq(
    "internal-auth.token" -> dummytoken
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "RouterConnectorSpec#post" should "return a balance if successful" in {
    val grn                     = arbitrary[GuaranteeReferenceNumber].sample.get
    val balanceRequest          = arbitrary[AccessCode].map(BalanceRequest(_)).sample.get
    val internalBalanceResponse = arbitrary[InternalBalanceResponse].sample.get

    wireMockServer.stubFor(
      post(urlEqualTo(s"/ctc-guarantee-balance-router/${grn.value}/balance"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.AUTHORIZATION, equalTo(dummytoken))
        .withRequestBody(equalToJson(Json.stringify(Json.toJson(balanceRequest))))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              Json.stringify(
                Json.toJson(internalBalanceResponse)
              )
            )
        )
    )

    val sut = injector.instanceOf[RouterConnector]
    sut.post(grn, balanceRequest).unsafeToFuture().map {
      result => result shouldBe Right(internalBalanceResponse)
    }
  }

  it should "return an error if not successful" in {
    val grn            = arbitrary[GuaranteeReferenceNumber].sample.get
    val balanceRequest = arbitrary[AccessCode].map(BalanceRequest(_)).sample.get
    val errorCode      = Gen.oneOf(ErrorCode.errorCodes).sample.get
    wireMockServer.stubFor(
      post(urlEqualTo(s"/ctc-guarantee-balance-router/${grn.value}/balance"))
        .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.CONTENT_TYPE, equalTo(ContentTypes.JSON))
        .withHeader(HeaderNames.AUTHORIZATION, equalTo(dummytoken))
        .withRequestBody(equalToJson(Json.stringify(Json.toJson(balanceRequest))))
        .willReturn(
          aResponse()
            .withStatus(errorCode.statusCode)
            .withBody(
              Json.stringify(
                Json.obj(
                  "code"    -> errorCode.code,
                  "message" -> "message"
                )
              )
            )
        )
    )

    val sut = injector.instanceOf[RouterConnector]
    sut.post(grn, balanceRequest).unsafeToFuture().map {
      case Left(UpstreamErrorResponse(_, errorCode.statusCode, _, _)) => succeed
      case x                                                          => fail(s"Expected failure with status code ${errorCode.statusCode}, but got $x instead")
    }

  }

  "RouterConnectorImpl#isFailure" should "return false when there is a successful call" in {
    val connector               = injector.instanceOf[RouterConnectorImpl]
    val internalBalanceResponse = arbitrary[InternalBalanceResponse].sample.get

    Future.successful(
      connector.isFailure(Success(Right(internalBalanceResponse))) shouldBe false
    )
  }

  it should "return false when there is a failed call but with an UpstreamErrorResponse" in {
    val connector = injector.instanceOf[RouterConnectorImpl]

    Future.successful(
      connector.isFailure(Success(Left(UpstreamErrorResponse("nope", NOT_FOUND)))) shouldBe false
    )
  }

  it should "return true when there is a failed call for any other exception" in {
    val connector = injector.instanceOf[RouterConnectorImpl]

    Future.successful(
      connector.isFailure(Success(Left(new IllegalArgumentException()))) shouldBe true
    )
  }

  it should "return true when there is a failed Try" in {
    val connector = injector.instanceOf[RouterConnectorImpl]

    Future.successful(
      connector.isFailure(Failure(new IllegalArgumentException())) shouldBe true
    )
  }

}
