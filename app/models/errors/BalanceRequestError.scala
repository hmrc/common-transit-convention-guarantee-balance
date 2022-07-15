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

import models.values.BalanceId
import play.api.libs.json._
import uk.gov.hmrc.http.UpstreamErrorResponse

sealed abstract class BalanceRequestError extends Product with Serializable {
  def message: String
}

case class NotFoundError(message: String) extends BalanceRequestError

case class UpstreamTimeoutError(message: String = "Request timed out") extends BalanceRequestError

case class InternalServiceError(
  message: String = "Internal server error",
  cause: Option[Throwable] = None
) extends BalanceRequestError

object InternalServiceError {

  def causedBy(cause: Throwable): BalanceRequestError =
    BalanceRequestError.internalServiceError(cause = Some(cause))
}

case class UpstreamServiceError(
  message: String = "Internal server error",
  cause: UpstreamErrorResponse
) extends BalanceRequestError

object UpstreamServiceError {

  def causedBy(cause: UpstreamErrorResponse): BalanceRequestError =
    BalanceRequestError.upstreamServiceError(cause = cause)
}

object BalanceRequestError {

  def upstreamServiceError(
    message: String = "Internal server error",
    cause: UpstreamErrorResponse
  ): BalanceRequestError =
    UpstreamServiceError(message, cause)

  def internalServiceError(
    message: String = "Internal server error",
    cause: Option[Throwable] = None
  ): BalanceRequestError =
    InternalServiceError(message, cause)

  def notFoundError(balanceId: BalanceId): BalanceRequestError =
    NotFoundError(
      s"The balance request with ID ${balanceId.value} was not found"
    )

  implicit lazy val upstreamServiceErrorWrites: OWrites[UpstreamServiceError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit lazy val internalServiceErrorWrites: OWrites[InternalServiceError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit lazy val upstreamTimeoutErrorWrites: OWrites[UpstreamTimeoutError] =
    (__ \ "message").write[String].contramap(_.message)

  implicit lazy val notFoundErrorWrites: OWrites[NotFoundError] =
    (__ \ "message").write[String].contramap(_.message)

  def withErrorCode(jsObject: JsObject, code: String): JsObject =
    jsObject ++ Json.obj(ErrorCode.FieldName -> code)

  implicit lazy val balanceRequestErrorWrites: OWrites[BalanceRequestError] =
    OWrites {
      case err @ UpstreamServiceError(_, _) =>
        withErrorCode(upstreamServiceErrorWrites.writes(err), ErrorCode.InternalServerError)

      case err @ InternalServiceError(_, _) =>
        withErrorCode(internalServiceErrorWrites.writes(err), ErrorCode.InternalServerError)

      case err @ UpstreamTimeoutError(_) =>
        withErrorCode(upstreamTimeoutErrorWrites.writes(err), ErrorCode.GatewayTimeout)

      case err @ NotFoundError(_) =>
        withErrorCode(notFoundErrorWrites.writes(err), ErrorCode.NotFound)
    }
}
