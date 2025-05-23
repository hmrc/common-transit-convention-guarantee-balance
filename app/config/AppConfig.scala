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

package config

import io.lemonlabs.uri.AbsoluteUrl
import io.lemonlabs.uri.UrlPath
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.duration.Duration

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig) {

  lazy val balanceRequestLockoutTtl: Duration =
    config.get[Duration]("balance-request.lockout-ttl")

  private lazy val routerBaseUrl: AbsoluteUrl =
    AbsoluteUrl.parse(servicesConfig.baseUrl("ctc-guarantee-balance-router"))

  private lazy val routerPath: UrlPath =
    UrlPath.parse(
      config.get[String]("microservice.services.ctc-guarantee-balance-router.path")
    )

  lazy val routerUrl: AbsoluteUrl =
    routerBaseUrl.withPath(routerPath)

  lazy val routerCircuitBreakerConfig: CircuitBreakerConfig =
    CircuitBreakerConfig.fromServicesConfig("ctc-guarantee-balance-router", config)

  lazy val internalAuthToken: String = config.get[String]("internal-auth.token")

}
