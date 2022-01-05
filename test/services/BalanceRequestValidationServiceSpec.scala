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

import cats.data.NonEmptyList
import models.errors.InvalidAccessCode
import models.errors.InvalidGuaranteeReference
import models.errors.InvalidTaxIdentifier
import models.request.BalanceRequest
import models.values.AccessCode
import models.values.GuaranteeReference
import models.values.TaxIdentifier
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BalanceRequestValidationServiceSpec extends AnyFlatSpec with Matchers with EitherValues {

  val validator = new BalanceRequestValidationService

  "BalanceRequestValidationService.validate" should "return balance request when validation is successful" in {
    val request = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    validator.validate(request).value shouldBe request
  }

  it should "return an error when tax identifier is empty" in {
    val request = BalanceRequest(
      TaxIdentifier(""),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    validator.validate(request).left.value shouldBe NonEmptyList.one(
      InvalidTaxIdentifier.nonEmpty
    )
  }

  it should "return an error when tax identifier is too long" in {
    val request = BalanceRequest(
      TaxIdentifier("GB1234567890012312312"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    validator.validate(request).left.value shouldBe NonEmptyList.one(
      InvalidTaxIdentifier.maxLength(17)
    )
  }

  it should "return an error when tax identifier contains invalid characters" in {
    val request = BalanceRequest(
      TaxIdentifier("GB123456@8900#"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("1234")
    )

    validator.validate(request).left.value shouldBe NonEmptyList.one(
      InvalidTaxIdentifier.alphanumeric
    )
  }

  it should "return an error when guarantee reference is empty" in {
    val request = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference(""),
      AccessCode("1234")
    )

    validator.validate(request).left.value shouldBe NonEmptyList.one(
      InvalidGuaranteeReference.nonEmpty
    )
  }

  it should "return an error when guarantee reference is too long" in {
    val request = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A0010178"),
      AccessCode("1234")
    )

    validator.validate(request).left.value shouldBe NonEmptyList.one(
      InvalidGuaranteeReference.maxLength(24)
    )
  }

  it should "return an error when guarantee reference contains invalid characters" in {
    val request = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE33@#¢E0001067A001017"),
      AccessCode("1234")
    )

    validator.validate(request).left.value shouldBe NonEmptyList.one(
      InvalidGuaranteeReference.alphanumeric
    )
  }

  it should "return an error when access code is too short" in {
    val request = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("234")
    )

    validator.validate(request).left.value shouldBe NonEmptyList.one(
      InvalidAccessCode.exactLength(4)
    )
  }

  it should "return an error when access code is too long" in {
    val request = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("12345678")
    )

    validator.validate(request).left.value shouldBe NonEmptyList.one(
      InvalidAccessCode.exactLength(4)
    )
  }

  it should "return an error when access code contains invalid characters" in {
    val request = BalanceRequest(
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      AccessCode("123]")
    )

    validator.validate(request).left.value shouldBe NonEmptyList.one(
      InvalidAccessCode.alphanumeric
    )
  }

  it should "return multiple errors when there are several problems" in {
    val request = BalanceRequest(
      TaxIdentifier("########################"),
      GuaranteeReference("##############"),
      AccessCode("#")
    )

    validator.validate(request).left.value shouldBe NonEmptyList.of(
      InvalidTaxIdentifier.maxLength(17),
      InvalidTaxIdentifier.alphanumeric,
      InvalidGuaranteeReference.alphanumeric,
      InvalidAccessCode.exactLength(4),
      InvalidAccessCode.alphanumeric
    )
  }
}
