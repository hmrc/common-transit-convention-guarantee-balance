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

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.OWrites
import uk.gov.hmrc.play.json.Union

sealed abstract class TooManyRequestsError(val message: String)

case class ApiRateLimitError(override val message: String) extends TooManyRequestsError(message)

case class GmsQueryLimitError(override val message: String) extends TooManyRequestsError(message)

object TooManyRequestsError {
  def apiRateLimit(message: String = "Too many requests"): TooManyRequestsError =
    ApiRateLimitError(message)

  def gmsQueryLimit(message: String = "Too many requests"): TooManyRequestsError =
    GmsQueryLimitError(message)

  implicit val tooManyRequestsErrorWrites: OWrites[TooManyRequestsError] =
    OWrites[TooManyRequestsError] { err =>
      Json.obj(
        ErrorCode.FieldName -> ErrorCode.TooManyRequests,
        "message"           -> err.message
      )
    }

  implicit val apiRateLimitErrorFormat: OFormat[ApiRateLimitError] =
    OFormat(
      Json.reads[ApiRateLimitError],
      // Should use `OWrites#narrow` added in Play JSON 2.9.x but it's not binary compatible
      tooManyRequestsErrorWrites.asInstanceOf[OWrites[ApiRateLimitError]]
    )

  implicit val gmsQueryLimitErrorFormat: OFormat[GmsQueryLimitError] =
    OFormat(
      Json.reads[GmsQueryLimitError],
      // Should use `OWrites#narrow` added in Play JSON 2.9.x but it's not binary compatible
      tooManyRequestsErrorWrites.asInstanceOf[OWrites[GmsQueryLimitError]]
    )

  implicit val tooManyRequestsErrorFormat: OFormat[TooManyRequestsError] =
    Union
      .from[TooManyRequestsError](RateLimitReason.FieldName)
      .and[ApiRateLimitError](RateLimitReason.ApiRateLimit)
      .and[GmsQueryLimitError](RateLimitReason.GmsQueryLimit)
      .format
}
