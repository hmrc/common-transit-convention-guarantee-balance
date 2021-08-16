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

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import uk.gov.hmrc.play.json.Union

sealed abstract class BalanceRequestError extends Product with Serializable {
  def statusCode: Int
  def message: String
}

case class BadRequestError(
  message: String,
  errors: List[BalanceRequestError] = List.empty
) extends BalanceRequestError {
  def statusCode: Int = 400
}

case class UpstreamServiceError(message: String = "Internal server error")
    extends BalanceRequestError {
  def statusCode: Int = 500
}
case class InternalServiceError(message: String = "Internal server error")
    extends BalanceRequestError {
  def statusCode: Int = 500
}

object BalanceRequestError {
  implicit def badRequestErrorFormat: OFormat[BadRequestError] =
    Json.format[BadRequestError]

  implicit def upstreamServiceErrorFormat: OFormat[UpstreamServiceError] =
    Json.format[UpstreamServiceError]

  implicit def internalServiceErrorFormat: OFormat[InternalServiceError] =
    Json.format[InternalServiceError]

  implicit def balanceRequestErrorFormat: OFormat[BalanceRequestError] =
    Union
      .from[BalanceRequestError](ErrorCode.FieldName)
      .andLazy[BadRequestError](ErrorCode.BadRequest, badRequestErrorFormat)
      .and[UpstreamServiceError](ErrorCode.InternalServerError)
      .and[InternalServiceError](ErrorCode.InternalServerError)
      .format
}
