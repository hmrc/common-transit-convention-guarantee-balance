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

import org.mockito.MockitoSugar
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import v2.generators.Generators

class BindersSpec extends AnyFlatSpec with Matchers with MockitoSugar with ScalaFutures with ScalaCheckDrivenPropertyChecks with Generators {

  "grnBinder" should "return a right if the GRN is valid" in forAll(arbitrary[GuaranteeReferenceNumber]) {
    grn =>
      Binders.grnBinder.bind("a", grn.value) shouldBe Right(grn)
  }

  it should "return a left if the GRN is a valid format, but not a GB/XI GRN" in forAll(guaranteeReferenceNumberGenerator(Gen.const("FR"))) {
    grn =>
      Binders.grnBinder.bind("a", grn.value) shouldBe Left("The guarantee reference number must be for a GB or XI guarantee.")
  }

  it should "return a left if the GRN is not a valid format" in forAll(Gen.stringOfN(10, Gen.alphaChar)) {
    grn =>
      Binders.grnBinder.bind("a", grn) shouldBe Left("The guarantee reference number is not in the correct format.")
  }

}
