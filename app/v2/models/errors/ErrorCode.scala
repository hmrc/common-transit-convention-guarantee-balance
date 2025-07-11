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

package v2.models.errors

import play.api.http.Status._
import play.api.libs.json.JsError
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.Reads
import play.api.libs.json.Writes

sealed abstract class ErrorCode(val code: String, val statusCode: Int) extends Product with Serializable

object ErrorCode {
  case object BadRequest                   extends ErrorCode("BAD_REQUEST", BAD_REQUEST)
  case object InvalidGuaranteeType         extends ErrorCode("INVALID_GUARANTEE_TYPE", BAD_REQUEST)
  case object NotFound                     extends ErrorCode("NOT_FOUND", NOT_FOUND)
  case object Forbidden                    extends ErrorCode("FORBIDDEN", FORBIDDEN)
  case object InternalServerError          extends ErrorCode("INTERNAL_SERVER_ERROR", INTERNAL_SERVER_ERROR)
  case object Unauthorized                 extends ErrorCode("UNAUTHORIZED", UNAUTHORIZED)
  case object NotAcceptable                extends ErrorCode("NOT_ACCEPTABLE", NOT_ACCEPTABLE)
  case object TooManyRequests              extends ErrorCode("MESSAGE_THROTTLED_OUT", TOO_MANY_REQUESTS) // to match API Platform
  case object Gone                         extends ErrorCode("GONE", GONE)
  private case object GatewayTimeout       extends ErrorCode("GATEWAY_TIMEOUT", GATEWAY_TIMEOUT)
  private case object EntityTooLarge       extends ErrorCode("REQUEST_ENTITY_TOO_LARGE", REQUEST_ENTITY_TOO_LARGE)
  private case object UnsupportedMediaType extends ErrorCode("UNSUPPORTED_MEDIA_TYPE", UNSUPPORTED_MEDIA_TYPE)

  lazy val errorCodes: Seq[ErrorCode] = Seq(
    BadRequest,
    InvalidGuaranteeType,
    NotFound,
    Forbidden,
    InternalServerError,
    GatewayTimeout,
    EntityTooLarge,
    UnsupportedMediaType,
    Unauthorized,
    NotAcceptable,
    TooManyRequests
  )

  implicit val errorCodeWrites: Writes[ErrorCode] = Writes {
    errorCode => JsString(errorCode.code)
  }

  implicit val errorCodeReads: Reads[ErrorCode] = Reads {
    errorCode =>
      errorCodes
        .find(_.code == errorCode.asInstanceOf[JsString].value)
        .map(JsSuccess(_))
        .getOrElse(JsError())
  }
}
