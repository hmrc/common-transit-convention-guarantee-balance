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

package models.response

import config.Constants
import io.lemonlabs.uri.RelativeUrl
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Writes
import play.api.mvc.Call

case class Link(href: RelativeUrl)

object Link {

  def apply(call: Call): Link =
    Link(RelativeUrl.parse(Constants.Context + call.path()))

  def self(call: Call) =
    "self" -> Link(call)

  def named(name: String, call: Call) =
    name -> Link(call)

  implicit val relativeUrlWrites: Writes[RelativeUrl] =
    Format.of[String].contramap(_.toString)

  implicit val linkWrites: OWrites[Link] =
    Json.writes[Link]
}
