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

package v2.services

import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.FORBIDDEN
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.RouterConnector
import v2.generators.Generators
import v2.models.BalanceRequest
import v2.models.GuaranteeReferenceNumber
import v2.models.InternalBalanceResponse
import v2.models.errors.RoutingError
import v2.models.errors.UpstreamError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class RouterServiceSpec extends AnyFlatSpec with Matchers with ScalaFutures with ScalaCheckDrivenPropertyChecks with Generators {

  implicit val hc: HeaderCarrier    = HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  val timeout: Timeout              = Timeout(1.second)

  "RouterService" should "return a Right with an internal balance response on success" in forAll(
    arbitrary[BalanceRequest],
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalBalanceResponse]
  ) {
    (balanceRequest, grn, response) =>
      val mockConnector = mock[RouterConnector]
      when(mockConnector.post(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(balanceRequest))(eqTo(hc), any))
        .thenReturn(Future.successful(Right(response)))

      val sut = new RouterServiceImpl(mockConnector)
      whenReady(sut.request(grn, balanceRequest).value) {
        result => result shouldBe Right(response)
      }
  }

  "RouterService" should "return a Left with an InvalidAccessCode response if the access code fails" in forAll(
    arbitrary[BalanceRequest],
    arbitrary[GuaranteeReferenceNumber]
  ) {
    (balanceRequest, grn) =>
      val mockConnector = mock[RouterConnector]
      when(mockConnector.post(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(balanceRequest))(eqTo(hc), any))
        .thenReturn(Future.successful(Left(UpstreamError("nope", FORBIDDEN, FORBIDDEN, Map.empty))))

      val sut = new RouterServiceImpl(mockConnector)
      whenReady(sut.request(grn, balanceRequest).value) {
        result => result shouldBe Left(RoutingError.InvalidAccessCode)
      }
  }

  "RouterService" should "return a Left with an InvalidAccessCode response if the GRN wasn't found" in forAll(
    arbitrary[BalanceRequest],
    arbitrary[GuaranteeReferenceNumber]
  ) {
    (balanceRequest, grn) =>
      val mockConnector = mock[RouterConnector]
      when(mockConnector.post(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(balanceRequest))(eqTo(hc), any))
        .thenReturn(Future.successful(Left(UpstreamError("nope", NOT_FOUND, NOT_FOUND, Map.empty))))

      val sut = new RouterServiceImpl(mockConnector)
      whenReady(sut.request(grn, balanceRequest).value) {
        result => result shouldBe Left(RoutingError.GuaranteeReferenceNotFound)
      }
  }

  "RouterService" should "return a Left with an InvalidGuaranteeType response if the guarantee type is invalid" in forAll(
    arbitrary[BalanceRequest],
    arbitrary[GuaranteeReferenceNumber]
  ) {
    (balanceRequest, grn) =>
      val mockConnector = mock[RouterConnector]
      when(mockConnector.post(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(balanceRequest))(eqTo(hc), any))
        .thenReturn(
          Future.successful(
            Left(UpstreamError("""{ "code":"INVALID_GUARANTEE_TYPE", "message":"Guarantee Type is not supported." }""", BAD_REQUEST, BAD_REQUEST, Map.empty))
          )
        )

      val sut = new RouterServiceImpl(mockConnector)
      whenReady(sut.request(grn, balanceRequest).value, timeout) {
        result => result shouldBe Left(RoutingError.InvalidGuaranteeType)
      }
  }

  "RouterService" should "return a Left with an UpstreamErrorResponse response if bad request was not invalid guarantee type" in forAll(
    arbitrary[BalanceRequest],
    arbitrary[GuaranteeReferenceNumber]
  ) {
    (balanceRequest, grn) =>
      val message       = """{ "code":"BAD_REQUEST", "message":"Guarantee Type is not supported." }"""
      val mockConnector = mock[RouterConnector]
      when(mockConnector.post(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(balanceRequest))(eqTo(hc), any))
        .thenReturn(
          Future.successful(
            Left(UpstreamError("""{ "code":"BAD_REQUEST", "message":"Guarantee Type is not supported." }""", BAD_REQUEST, BAD_REQUEST, Map.empty))
          )
        )

      val sut      = new RouterServiceImpl(mockConnector)
      val EmptyMap = Map.empty[String, Seq[String]]
      whenReady(sut.request(grn, balanceRequest).value, timeout) {
        case Left(RoutingError.Unexpected(Some(UpstreamErrorResponse(`message`, BAD_REQUEST, BAD_REQUEST, EmptyMap)))) => succeed
        case x => fail(s"Expected Left(Unexpected(Some(UpstreamErrorResponse))), got $x")
      }
  }

  "RouterService" should "return a Left with an Unexpected if another error occurred" in forAll(
    arbitrary[BalanceRequest],
    arbitrary[GuaranteeReferenceNumber]
  ) {
    (balanceRequest, grn) =>
      val exception     = UpstreamErrorResponse("nope", INTERNAL_SERVER_ERROR)
      val mockConnector = mock[RouterConnector]
      when(mockConnector.post(GuaranteeReferenceNumber(eqTo(grn.value)), eqTo(balanceRequest))(eqTo(hc), any))
        .thenReturn(Future.successful(Left(exception)))

      val sut = new RouterServiceImpl(mockConnector)
      whenReady(sut.request(grn, balanceRequest).value) {
        result => result shouldBe Left(RoutingError.Unexpected(Some(exception)))
      }
  }

}
