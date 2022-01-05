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

package models.errors

import cats.data.NonEmptyList
import models.formats.CommonFormats
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import uk.gov.hmrc.play.json.Union

sealed abstract class BadRequestError {
  def message: String
}

case class MultipleErrors(message: String = "Bad request", errors: NonEmptyList[BadRequestError])
  extends BadRequestError

case class InvalidTaxIdentifier(message: String = "Invalid tax identifier value", reason: String)
  extends BadRequestError

object InvalidTaxIdentifier {
  val nonEmpty =
    InvalidTaxIdentifier(reason = "Tax identifier must not be empty")

  val alphanumeric =
    InvalidTaxIdentifier(reason = "Tax identifier must be alphanumeric")

  def maxLength(length: Int) =
    InvalidTaxIdentifier(reason = s"Tax identifier has a maximum length of $length characters")
}

case class InvalidGuaranteeReference(
  message: String = "Invalid guarantee reference value",
  reason: String
) extends BadRequestError

object InvalidGuaranteeReference {
  val nonEmpty =
    InvalidGuaranteeReference(reason = "Guarantee reference must not be empty")

  val alphanumeric =
    InvalidGuaranteeReference(reason = "Guarantee reference must be alphanumeric")

  def maxLength(length: Int) = InvalidGuaranteeReference(reason =
    s"Guarantee reference has a maximum length of $length characters"
  )
}

case class InvalidAccessCode(
  message: String = "Invalid access code value",
  reason: String
) extends BadRequestError

object InvalidAccessCode {
  val alphanumeric =
    InvalidAccessCode(reason = "Access code must be alphanumeric")

  def exactLength(length: Int) =
    InvalidAccessCode(reason = s"Access code must be $length characters in length")
}

object BadRequestError extends CommonFormats {
  implicit lazy val multipleErrorsFormat: OFormat[MultipleErrors] =
    Json.format[MultipleErrors]

  implicit val invalidTaxIdentifierFormat: OFormat[InvalidTaxIdentifier] =
    Json.format[InvalidTaxIdentifier]

  implicit val invalidGuaranteeReferenceFormat: OFormat[InvalidGuaranteeReference] =
    Json.format[InvalidGuaranteeReference]

  implicit val invalidAccessCodeFormat: OFormat[InvalidAccessCode] =
    Json.format[InvalidAccessCode]

  implicit val badRequestErrorFormat: Format[BadRequestError] = Union
    .from[BadRequestError](ErrorCode.FieldName)
    .and[InvalidTaxIdentifier](ErrorCode.InvalidTaxIdentifier)
    .and[InvalidGuaranteeReference](ErrorCode.InvalidGuaranteeReference)
    .and[InvalidAccessCode](ErrorCode.InvalidAccessCode)
    .andLazy[MultipleErrors](ErrorCode.BadRequest, multipleErrorsFormat)
    .format
}
