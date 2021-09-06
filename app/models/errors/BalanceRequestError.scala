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
  def message: String
}

case class UpstreamServiceError(message: String = "Internal server error")
    extends BalanceRequestError

case class UpstreamTimeoutError(message: String = "Request timed out") extends BalanceRequestError

case class InternalServiceError(message: String = "Internal server error")
    extends BalanceRequestError

object BalanceRequestError {
  def upstreamServiceError(message: String = "Internal server error"): BalanceRequestError =
    UpstreamServiceError(message)

  def internalServiceError(message: String = "Internal server error"): BalanceRequestError =
    InternalServiceError(message)

  implicit def upstreamServiceErrorFormat: OFormat[UpstreamServiceError] =
    Json.format[UpstreamServiceError]

  implicit def internalServiceErrorFormat: OFormat[InternalServiceError] =
    Json.format[InternalServiceError]

  implicit def upstreamTimeoutErrorFormat: OFormat[UpstreamTimeoutError] =
    Json.format[UpstreamTimeoutError]

  implicit def balanceRequestErrorFormat: OFormat[BalanceRequestError] =
    Union
      .from[BalanceRequestError](ErrorCode.FieldName)
      .and[UpstreamServiceError](ErrorCode.InternalServerError)
      .and[UpstreamTimeoutError](ErrorCode.GatewayTimeout)
      .and[InternalServiceError](ErrorCode.InternalServerError)
      .format
}
