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

import org.scalatest.Ignore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// TODO: Pending while waiting for play-json-union-formatter PR to be merged
@Ignore
class ErrorFormatsSpec extends AnyFlatSpec with Matchers {
  "BalanceRequestError.balanceRequestErrorFormat" should "produce errors following HMRC Reference Guide for InternalServiceError" in {
    val error = InternalServiceError()
    val json  = BalanceRequestError.balanceRequestErrorFormat.writes(error)
    (json \ "code").as[String] shouldBe "INTERNAL_SERVER_ERROR"
    (json \ "message").as[String] shouldBe "Internal server error"
  }

  it should "produce errors following HMRC Reference Guide for UpstreamServiceError" in {
    val error = UpstreamServiceError()
    val json  = BalanceRequestError.balanceRequestErrorFormat.writes(error)
    (json \ "code").as[String] shouldBe "INTERNAL_SERVER_ERROR"
    (json \ "message").as[String] shouldBe "Internal server error"
  }
}
