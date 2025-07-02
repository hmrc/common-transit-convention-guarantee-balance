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

import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import v2.generators.Generators
import v2.models.AccessCode
import v2.models.BalanceRequest
import v2.models.errors.ValidationError
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.duration.DurationInt

class ValidationServiceSpec extends AnyFlatSpec with Matchers with ScalaFutures with ScalaCheckDrivenPropertyChecks with Generators {

  val sut              = new ValidationServiceImpl
  val timeout: Timeout = Timeout(1.second)
  "ValidationService#validate" should "validate a valid request" in forAll(arbitrary[BalanceRequest]) {
    balanceRequest =>
      whenReady(sut.validate(balanceRequest).value, timeout) {
        r => r shouldBe Right(())
      }
  }

  it should "return an error if we have a access code of length 5" in forAll(
    arbitrary[BalanceRequest],
    Gen.stringOfN(5, Gen.alphaNumChar).map(AccessCode(_))
  ) {
    (balanceRequest, brokenAccessCode) =>
      whenReady(sut.validate(balanceRequest.copy(accessCode = brokenAccessCode)).value) {
        r => r shouldBe Left(NonEmptyList.one(ValidationError.InvalidAccessCodeLength(brokenAccessCode)))
      }
  }

  it should "return an error if we have a access code with invalid characters" in forAll(
    arbitrary[BalanceRequest],
    Gen
      .stringOfN(3, Gen.alphaNumChar)
      .map(
        a => AccessCode(s"$a?")
      )
  ) {
    (balanceRequest, brokenAccessCode) =>
      whenReady(sut.validate(balanceRequest.copy(accessCode = brokenAccessCode)).value) {
        r => r shouldBe Left(NonEmptyList.one(ValidationError.InvalidAccessCodeCharacters(brokenAccessCode)))
      }
  }

  it should "return two errors if we have a access code with invalid characters and of the wrong length" in forAll(
    arbitrary[BalanceRequest],
    Gen
      .stringOfN(4, Gen.alphaNumChar)
      .map(
        a => AccessCode(s"$a?")
      )
  ) {
    (balanceRequest, brokenAccessCode) =>
      whenReady(sut.validate(balanceRequest.copy(accessCode = brokenAccessCode)).value) {
        r =>
          r shouldBe Left(
            NonEmptyList.of(
              ValidationError.InvalidAccessCodeLength(brokenAccessCode),
              ValidationError.InvalidAccessCodeCharacters(brokenAccessCode)
            )
          )
      }
  }

}
