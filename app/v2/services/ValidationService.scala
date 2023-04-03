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

import cats.data.EitherT
import cats.data.NonEmptyList
import cats.data.Validated
import cats.effect.IO
import cats.implicits.catsSyntaxTuple2Semigroupal
import cats.implicits.toFunctorOps
import com.google.inject.ImplementedBy
import v2.models.AccessCode
import v2.models.BalanceRequest
import v2.models.errors.ValidationError

@ImplementedBy(classOf[ValidationServiceImpl])
trait ValidationService {

  def validate(payload: BalanceRequest): EitherT[IO, NonEmptyList[ValidationError], Unit]

}

class ValidationServiceImpl extends ValidationService {

  type ValidateResult = Validated[NonEmptyList[ValidationError], Unit]

  def exactLength(value: String, length: Int, error: Int => ValidationError): ValidateResult =
    Validated.condNel(value.length == length, (), error(length))

  def alphanumeric(value: String, error: => ValidationError): ValidateResult =
    Validated.condNel(value.forall(_.isLetterOrDigit), (), error)

  def validateAccessCode(code: AccessCode): ValidateResult =
    (
      exactLength(code.value, 4, _ => ValidationError.InvalidAccessCodeLength(code)),
      alphanumeric(code.value, ValidationError.InvalidAccessCodeCharacters(code))
    ).tupled.void

  override def validate(payload: BalanceRequest): EitherT[IO, NonEmptyList[ValidationError], Unit] = EitherT {
    IO {
      validateAccessCode(payload.masterAccessCode).toEither
    }
  }

}
