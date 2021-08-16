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

package models.errors

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

class ErrorFormatsSpec extends AnyFlatSpec with Matchers {
  "BalanceRequestError.balanceRequestErrorFormat" should "produce errors following HMRC Reference Guide for InternalServiceError" in {
    val error = InternalServiceError()
    val json  = BalanceRequestError.balanceRequestErrorFormat.writes(error)
    json shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "produce errors following HMRC Reference Guide for UpstreamServiceError" in {
    val error = UpstreamServiceError()
    val json  = BalanceRequestError.balanceRequestErrorFormat.writes(error)
    json shouldBe Json.obj(
      "code"    -> "INTERNAL_SERVER_ERROR",
      "message" -> "Internal server error"
    )
  }

  it should "produce errors following HMRC Reference Guide for BadRequestError" in {
    val error = BadRequestError(
      "Argh!!",
      List(BadRequestError("I don't like field 1!"), BadRequestError("I don't like field 2!"))
    )
    val json = BalanceRequestError.balanceRequestErrorFormat.writes(error)
    json shouldBe Json.obj(
      "code"    -> "BAD_REQUEST",
      "message" -> "Argh!!",
      "errors" -> Json.arr(
        Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "I don't like field 1!",
          "errors"  -> Json.arr()
        ),
        Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "I don't like field 2!",
          "errors"  -> Json.arr()
        )
      )
    )
  }
}
