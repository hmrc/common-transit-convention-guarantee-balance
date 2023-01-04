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

package models.errors

import models.values.BalanceId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.util.UUID

class ErrorFormatsSpec extends AnyFlatSpec with Matchers {
  "BalanceRequestError.balanceRequestErrorFormat" should "produce a valid format for InternalServiceError" in {
    val error = InternalServiceError()
    val json  = BalanceRequestError.balanceRequestErrorWrites.writes(error)
    json shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "produce a valid format for UpstreamServiceError" in {
    val error = UpstreamServiceError.causedBy(UpstreamErrorResponse("Argh!!!", 400))
    val json  = BalanceRequestError.balanceRequestErrorWrites.writes(error)
    json shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "produce a valid format for UpstreamTimeoutError" in {
    val error = UpstreamTimeoutError()
    val json  = BalanceRequestError.balanceRequestErrorWrites.writes(error)
    json shouldBe Json.obj(
      "code"    -> "GATEWAY_TIMEOUT",
      "message" -> "Request timed out"
    )
  }

  it should "produce a valid format for NotFoundError" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)
    val error     = BalanceRequestError.notFoundError(balanceId)
    val json      = BalanceRequestError.balanceRequestErrorWrites.writes(error)
    json shouldBe Json.obj(
      "code"    -> "NOT_FOUND",
      "message" -> "The balance request with ID 22b9899e-24ee-48e6-a189-97d1f45391c4 was not found"
    )
  }
}
