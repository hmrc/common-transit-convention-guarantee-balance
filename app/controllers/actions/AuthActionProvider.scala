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

package controllers.actions

import com.google.inject.ImplementedBy
import models.request.AuthenticatedRequest
import play.api.mvc.ActionBuilder
import play.api.mvc.AnyContent
import play.api.mvc.DefaultActionBuilder

import javax.inject.Inject
import javax.inject.Singleton

@ImplementedBy(classOf[AuthActionProviderImpl])
trait AuthActionProvider {
  def apply(): ActionBuilder[AuthenticatedRequest, AnyContent]
}

@Singleton
class AuthActionProviderImpl @Inject() (
  buildDefault: DefaultActionBuilder,
  authenticate: AuthAction
) extends AuthActionProvider {

  override def apply(): ActionBuilder[AuthenticatedRequest, AnyContent] =
    buildDefault andThen authenticate
}
