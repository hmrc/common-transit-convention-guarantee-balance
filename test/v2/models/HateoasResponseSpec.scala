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

package v2.models

import cats.effect.unsafe.implicits.global
import org.mockito.MockitoSugar
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import v2.generators.Generators

class HateoasResponseSpec extends AnyFlatSpec with Matchers with MockitoSugar with ScalaFutures with ScalaCheckDrivenPropertyChecks with Generators {

  "HateoasResponse#apply" should "return an appropriate Hateoas response" in forAll(
    arbitrary[GuaranteeReferenceNumber],
    arbitrary[Balance]
  ) {
    (grn, amount) =>
      val expected = Json.obj(
        "_links" -> Json.obj(
          "self" -> Json.obj(
            "href" -> s"/customs/guarantees/${grn.value}/balance"
          )
        ),
        "balance" -> amount.value
      )

      whenReady(HateoasResponse(grn, InternalBalanceResponse(amount)).unsafeToFuture()) {
        _ shouldBe expected
      }
  }

}
