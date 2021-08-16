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

package models.response

import controllers.routes.BalanceRequestController
import models.values.BalanceId
import play.api.libs.json.Json
import play.api.libs.json.OWrites

sealed abstract class HalResponse {
  def _links: Option[Map[String, Link]]
  def _embedded: Option[Map[String, HalResponse]]
}

case class PostBalanceRequestResponse(
  _links: Option[Map[String, Link]],
  _embedded: Option[Map[String, HalResponse]] = None,
  balanceId: BalanceId
) extends HalResponse

object PostBalanceRequestResponse {
  def apply(id: BalanceId): PostBalanceRequestResponse = {
    val selfRoute = BalanceRequestController.getBalanceRequest(id)
    PostBalanceRequestResponse(
      _links = Links(Link.self(selfRoute)),
      balanceId = id
    )
  }
}

object HalResponse {
  implicit lazy val balanceRequestResponseWrites: OWrites[PostBalanceRequestResponse] =
    Json.writes[PostBalanceRequestResponse]

  implicit lazy val halResponseWrites: OWrites[HalResponse] =
    OWrites { case postResponse: PostBalanceRequestResponse =>
      balanceRequestResponseWrites.writes(postResponse)
    }
}
