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

package services

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import connectors.FakeBalanceRequestConnector
import models.errors.InternalServiceError
import models.errors.UpstreamServiceError
import models.request.BalanceRequest
import models.values._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse

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

  def service(sendRequestResponse: IO[Either[UpstreamErrorResponse, BalanceId]]) =
    new BalanceRequestService(FakeBalanceRequestConnector(sendRequestResponse))

  "BalanceRequestService" should "return balance ID when successful" in {
    service(IO.pure(Right(balanceId)))
      .submitBalanceRequest(request)
      .map {
        _ shouldBe Right(balanceId)
      }
      .unsafeToFuture()
  }

  it should "return InternalServiceError when there is an upstream client error" in {
    service(IO.pure(Left(UpstreamErrorResponse("Argh!!!!!", 400))))
      .submitBalanceRequest(request)
      .map {
        _ shouldBe Left(InternalServiceError())
      }
      .unsafeToFuture()
  }

  it should "return UpstreamServiceError when there is an upstream server error" in {
    service(IO.pure(Left(UpstreamErrorResponse("Argh!!!!!", 502))))
      .submitBalanceRequest(request)
      .map {
        _ shouldBe Left(UpstreamServiceError())
      }
      .unsafeToFuture()
  }
}
