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

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.MockitoSugar
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
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

class RouterServiceSpec extends AnyFlatSpec with Matchers with MockitoSugar with ScalaFutures with ScalaCheckDrivenPropertyChecks with Generators {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "RouterService" should "return a Right with an internal balance response on success" in forAll(
    arbitrary[BalanceRequest],
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[InternalBalanceResponse]
  ) {
    (balanceRequest, grn, response) =>
      val mockConnector = mock[RouterConnector]
      when(mockConnector.get(GuaranteeReferenceNumber(eqTo(grn.value)))(eqTo(hc)))
        .thenReturn(IO.pure(Right(response)))

      val sut = new RouterServiceImpl(mockConnector)
      whenReady(sut.request(grn, balanceRequest).value.unsafeToFuture()) {
        result => result shouldBe Right(response)
      }
  }

  "RouterService" should "return a Left with an InvalidAccessCode response if the access code fails" in forAll(
    arbitrary[BalanceRequest],
    arbitrary[GuaranteeReferenceNumber]
  ) {
    (balanceRequest, grn) =>
      val mockConnector = mock[RouterConnector]
      when(mockConnector.get(GuaranteeReferenceNumber(eqTo(grn.value)))(eqTo(hc)))
        .thenReturn(IO.pure(Left(UpstreamErrorResponse("nope", FORBIDDEN))))

      val sut = new RouterServiceImpl(mockConnector)
      whenReady(sut.request(grn, balanceRequest).value.unsafeToFuture()) {
        result => result shouldBe Left(RoutingError.InvalidAccessCode)
      }
  }

  "RouterService" should "return a Left with an InvalidAccessCode response if the GRN wasn't found" in forAll(
    arbitrary[BalanceRequest],
    arbitrary[GuaranteeReferenceNumber]
  ) {
    (balanceRequest, grn) =>
      val mockConnector = mock[RouterConnector]
      when(mockConnector.get(GuaranteeReferenceNumber(eqTo(grn.value)))(eqTo(hc)))
        .thenReturn(IO.pure(Left(UpstreamErrorResponse("nope", NOT_FOUND))))

      val sut = new RouterServiceImpl(mockConnector)
      whenReady(sut.request(grn, balanceRequest).value.unsafeToFuture()) {
        result => result shouldBe Left(RoutingError.GuaranteeReferenceNotFound)
      }
  }

  "RouterService" should "return a Left with an Unexpected if another error occurred" in forAll(
    arbitrary[BalanceRequest],
    arbitrary[GuaranteeReferenceNumber]
  ) {
    (balanceRequest, grn) =>
      val exception     = UpstreamErrorResponse("nope", INTERNAL_SERVER_ERROR)
      val mockConnector = mock[RouterConnector]
      when(mockConnector.get(GuaranteeReferenceNumber(eqTo(grn.value)))(eqTo(hc)))
        .thenReturn(IO.pure(Left(exception)))

      val sut = new RouterServiceImpl(mockConnector)
      whenReady(sut.request(grn, balanceRequest).value.unsafeToFuture()) {
        result => result shouldBe Left(RoutingError.Unexpected(Some(exception)))
      }
  }

}
