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

  private lazy val backendBaseUrl: AbsoluteUrl =
    AbsoluteUrl.parse(servicesConfig.baseUrl("transit-movements-guarantee-balance"))
  private lazy val backendPath: UrlPath =
    UrlPath.parse(
      config.get[String]("microservice.services.transit-movements-guarantee-balance.path")
    )
  lazy val backendUrl: AbsoluteUrl =
    backendBaseUrl.withPath(backendPath)

  lazy val features = config.getOptional[Map[String, Boolean]]("features").getOrElse(Map.empty)

  lazy val asyncBalanceResponse = features.get("async-balance-response").getOrElse(false)

  lazy val backendCircuitBreakerConfig =
    CircuitBreakerConfig.fromServicesConfig("transit-movements-guarantee-balance", config)

  lazy val balanceRequestLockoutTtl =
    config.get[Duration]("balance-request.lockout-ttl")
}
