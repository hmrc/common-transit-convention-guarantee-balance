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

package controllers

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import controllers.actions.AuthActionProvider
import controllers.actions.IOActions
import models.request.BalanceRequest
import models.response.PostBalanceRequestResponse
import models.values.BalanceId
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import services.BalanceRequestService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceRequestController @Inject() (
  authenticate: AuthActionProvider,
  service: BalanceRequestService,
  cc: ControllerComponents,
  val runtime: IORuntime
) extends BackendController(cc)
    with IOActions
    with LocationWithContext {

  def submitBalanceRequest: Action[BalanceRequest] =
    authenticate().io(parse.json[BalanceRequest]) { implicit request =>
      service.submitBalanceRequest(request.body).map {
        case Left(error) =>
          Status(error.statusCode)(Json.toJson(error))
        case Right(id) =>
          Accepted(Json.toJson(PostBalanceRequestResponse(id))).withHeaders(
            HeaderNames.LOCATION -> routes.BalanceRequestController
              .getBalanceRequest(id)
              .pathWithContext
          )
      }
    }

  def getBalanceRequest(id: BalanceId): Action[AnyContent] =
    authenticate().io {
      IO.pure(NotFound)
    }
}
