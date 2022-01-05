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

package models.response

import controllers.routes.BalanceRequestController
import models.backend.BalanceRequestFunctionalError
import models.backend.BalanceRequestSuccess
import models.backend.PendingBalanceRequest
import models.errors.ErrorCode
import models.values.BalanceId
import play.api.libs.json.Json
import play.api.libs.json.OWrites

sealed abstract class HalResponse {
  def _links: Option[Map[String, Link]]
  def _embedded: Option[Map[String, HalResponse]]
}

case class PostBalanceRequestPendingResponse(
  _links: Option[Map[String, Link]],
  _embedded: Option[Map[String, HalResponse]] = None,
  balanceId: BalanceId
) extends HalResponse

object PostBalanceRequestPendingResponse {
  def apply(id: BalanceId): PostBalanceRequestPendingResponse = {
    val selfRoute = BalanceRequestController.getBalanceRequest(id)
    PostBalanceRequestPendingResponse(
      _links = Links(Link.self(selfRoute)),
      balanceId = id
    )
  }
}

case class PostBalanceRequestSuccessResponse(
  _links: Option[Map[String, Link]],
  _embedded: Option[Map[String, HalResponse]] = None,
  response: BalanceRequestSuccess
) extends HalResponse

object PostBalanceRequestSuccessResponse {
  def apply(response: BalanceRequestSuccess): PostBalanceRequestSuccessResponse = {
    PostBalanceRequestSuccessResponse(
      _links = None,
      response = response
    )
  }
}

case class PostBalanceRequestFunctionalErrorResponse(
  _links: Option[Map[String, Link]],
  _embedded: Option[Map[String, HalResponse]] = None,
  code: String,
  message: String,
  response: BalanceRequestFunctionalError
) extends HalResponse

object PostBalanceRequestFunctionalErrorResponse {
  def apply(response: BalanceRequestFunctionalError): PostBalanceRequestFunctionalErrorResponse = {
    PostBalanceRequestFunctionalErrorResponse(
      _links = None,
      code = ErrorCode.FunctionalError,
      message = "The request was rejected by the guarantee management system",
      response = response
    )
  }
}

case class GetBalanceRequestResponse(
  _links: Option[Map[String, Link]],
  _embedded: Option[Map[String, HalResponse]] = None,
  request: PendingBalanceRequest
) extends HalResponse

object GetBalanceRequestResponse {
  def apply(balanceId: BalanceId, request: PendingBalanceRequest): GetBalanceRequestResponse = {
    val selfRoute = BalanceRequestController.getBalanceRequest(balanceId)
    GetBalanceRequestResponse(
      _links = Links(Link.self(selfRoute)),
      request = request
    )
  }
}

object HalResponse {
  implicit lazy val balanceRequestPendingResponseWrites
    : OWrites[PostBalanceRequestPendingResponse] =
    Json.writes[PostBalanceRequestPendingResponse]

  implicit lazy val balanceRequestSuccessResponseWrites
    : OWrites[PostBalanceRequestSuccessResponse] =
    Json.writes[PostBalanceRequestSuccessResponse]

  implicit lazy val balanceRequestFunctionalErrorResponseWrites
    : OWrites[PostBalanceRequestFunctionalErrorResponse] =
    Json.writes[PostBalanceRequestFunctionalErrorResponse]

  implicit lazy val getBalanceRequestResponseWrites: OWrites[GetBalanceRequestResponse] =
    Json.writes[GetBalanceRequestResponse]

  implicit lazy val halResponseWrites: OWrites[HalResponse] =
    OWrites {
      case postResponse: PostBalanceRequestPendingResponse =>
        balanceRequestPendingResponseWrites.writes(postResponse)
      case postResponse: PostBalanceRequestSuccessResponse =>
        balanceRequestSuccessResponseWrites.writes(postResponse)
      case postResponse: PostBalanceRequestFunctionalErrorResponse =>
        balanceRequestFunctionalErrorResponseWrites.writes(postResponse)
      case getResponse: GetBalanceRequestResponse =>
        getBalanceRequestResponseWrites.writes(getResponse)
    }
}
