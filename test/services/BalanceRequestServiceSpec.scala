/*
 * Copyright 2022 HM Revenue & Customs
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

package services

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import connectors.FakeBalanceRequestConnector
import models.backend.BalanceRequestResponse
import models.backend.BalanceRequestSuccess
import models.backend.PendingBalanceRequest
import models.errors.BalanceRequestError
import models.errors.InternalServiceError
import models.errors.UpstreamServiceError
import models.request.BalanceRequest
import models.values._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class BalanceRequestServiceSpec extends AsyncFlatSpec with Matchers {

  implicit val hc = HeaderCarrier()

  val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
  val balanceId = BalanceId(uuid)

  val request = BalanceRequest(
    TaxIdentifier("GB12345678900"),
    GuaranteeReference("05DE3300BE0001067A001017"),
    AccessCode("1234")
  )

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

  def service(
    getRequestResponse: IO[Option[Either[UpstreamErrorResponse, PendingBalanceRequest]]] = IO.stub,
    sendRequestResponse: IO[
      Either[UpstreamErrorResponse, Either[BalanceId, BalanceRequestResponse]]
    ] = IO.stub
  ) =
    new BalanceRequestService(FakeBalanceRequestConnector(getRequestResponse, sendRequestResponse))

  "BalanceRequestService.submitBalanceRequest" should "pass through sync response" in {
    service(sendRequestResponse = IO.pure(Right(Right(balanceRequestSuccess))))
      .submitBalanceRequest(request)
      .map {
        _ shouldBe Right(Right(balanceRequestSuccess))
      }
      .unsafeToFuture()
  }

  it should "pass through async response" in {
    service(sendRequestResponse = IO.pure(Right(Left(balanceId))))
      .submitBalanceRequest(request)
      .map {
        _ shouldBe Right(Left(balanceId))
      }
      .unsafeToFuture()
  }

  it should "return InternalServiceError when there is an upstream client error" in {
    val error = UpstreamErrorResponse("Argh!!!!!", 400)
    service(sendRequestResponse = IO.pure(Left(error)))
      .submitBalanceRequest(request)
      .map {
        _ shouldBe Left(InternalServiceError.causedBy(error))
      }
      .unsafeToFuture()
  }

  it should "return UpstreamServiceError when there is an upstream server error" in {
    val error = UpstreamErrorResponse("Argh!!!!!", 502)

    service(sendRequestResponse = IO.pure(Left(error)))
      .submitBalanceRequest(request)
      .map {
        _ shouldBe Left(UpstreamServiceError.causedBy(error))
      }
      .unsafeToFuture()
  }

  "BalanceRequestService.getBalanceRequest" should "return the pending balance request when everything is successful" in {
    service(getRequestResponse = IO.some(Right(pendingBalanceRequest)))
      .getBalanceRequest(balanceId)
      .map {
        _ shouldBe Right(pendingBalanceRequest)
      }
      .unsafeToFuture()
  }

  it should "return NotFoundError when no balance request for the corresponding ID can be found" in {
    service(getRequestResponse = IO.none)
      .getBalanceRequest(balanceId)
      .map {
        _ shouldBe Left(BalanceRequestError.notFoundError(balanceId))
      }
      .unsafeToFuture()
  }

  it should "return InternalServiceError when there is an upstream client error" in {
    val error = UpstreamErrorResponse("Argh!!!!!", 400)

    service(getRequestResponse = IO.some(Left(error)))
      .getBalanceRequest(balanceId)
      .map {
        _ shouldBe Left(InternalServiceError.causedBy(error))
      }
      .unsafeToFuture()
  }

  it should "return UpstreamServiceError when there is an upstream server error" in {
    val error = UpstreamErrorResponse("Argh!!!!!", 502)
    service(getRequestResponse = IO.some(Left(error)))
      .getBalanceRequest(balanceId)
      .map {
        _ shouldBe Left(UpstreamServiceError.causedBy(error))
      }
      .unsafeToFuture()
  }
}
