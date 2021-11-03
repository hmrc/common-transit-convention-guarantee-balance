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

import cats.data.NonEmptyList
import cats.data.Validated
import cats.syntax.all._
import models.errors.BadRequestError
import models.errors.InvalidAccessCode
import models.errors.InvalidGuaranteeReference
import models.errors.InvalidTaxIdentifier
import models.request.BalanceRequest
import models.values.AccessCode
import models.values.GuaranteeReference
import models.values.TaxIdentifier

import javax.inject.Inject

class BalanceRequestValidationService @Inject() () {
  type Validate[A] = Validated[NonEmptyList[BadRequestError], A]

  def nonEmpty(value: String, error: => BadRequestError): Validate[Unit] =
    Validated.condNel(value.nonEmpty, (), error)

  def minLength(value: String, length: Int, error: Int => BadRequestError): Validate[Unit] =
    Validated.condNel(value.length >= length, (), error(length))

  def maxLength(value: String, length: Int, error: Int => BadRequestError): Validate[Unit] =
    Validated.condNel(value.length <= length, (), error(length))

  def exactLength(value: String, length: Int, error: Int => BadRequestError): Validate[Unit] =
    Validated.condNel(value.length == length, (), error(length))

  def alphanumeric(value: String, error: => BadRequestError): Validate[Unit] =
    Validated.condNel(value.forall(_.isLetterOrDigit), (), error)

  def validateTaxIdentifier(taxId: TaxIdentifier): Validate[Unit] =
    (
      nonEmpty(taxId.value, InvalidTaxIdentifier.nonEmpty),
      maxLength(taxId.value, 17, InvalidTaxIdentifier.maxLength),
      alphanumeric(taxId.value, InvalidTaxIdentifier.alphanumeric)
    ).tupled.void

  def validateGuaranteeReference(grn: GuaranteeReference): Validate[Unit] =
    (
      nonEmpty(grn.value, InvalidGuaranteeReference.nonEmpty),
      maxLength(grn.value, 24, InvalidGuaranteeReference.maxLength),
      alphanumeric(grn.value, InvalidGuaranteeReference.alphanumeric)
    ).tupled.void

  def validateAccessCode(code: AccessCode): Validate[Unit] =
    (
      exactLength(code.value, 4, InvalidAccessCode.exactLength),
      alphanumeric(code.value, InvalidAccessCode.alphanumeric)
    ).tupled.void

  def validate(request: BalanceRequest): Either[NonEmptyList[BadRequestError], BalanceRequest] =
    (
      validateTaxIdentifier(request.taxIdentifier),
      validateGuaranteeReference(request.guaranteeReference),
      validateAccessCode(request.accessCode)
    ).tupled.as(request).toEither
}
