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

package fakes.controllers.actions

import controllers.actions.AuthActionProvider
import models.request.AuthenticatedRequest
import play.api.mvc._
import play.api.test.Helpers.stubControllerComponents

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object FakeIntegrationAuthActionProvider extends AuthActionProvider {

  private lazy val cc = stubControllerComponents()

  override def apply(): ActionBuilder[AuthenticatedRequest, AnyContent] =
    cc.actionBuilder andThen FakeIntegrationAuthAction
}

object FakeIntegrationAuthAction extends ActionTransformer[Request, AuthenticatedRequest] {

  override protected def executionContext: ExecutionContext = ExecutionContext.global

  override protected def transform[A](request: Request[A]): Future[AuthenticatedRequest[A]] =
    request match {
      case x: AuthenticatedRequest[A] => Future.successful(x)
      case _                          => Future.failed(new Exception("Non-authenticated request"))
    }
}
