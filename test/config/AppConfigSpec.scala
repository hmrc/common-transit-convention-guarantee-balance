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
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration._

class AppConfigSpec extends AnyFlatSpec with Matchers {

  def mkAppConfig(config: Configuration) = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  "AppConfig" should "deserialize backend service config" in {
    val appConfig = mkAppConfig(
      Configuration(
        "microservice.services.transit-movements-guarantee-balance.protocol"                                   -> "https",
        "microservice.services.transit-movements-guarantee-balance.host"                                       -> "foo",
        "microservice.services.transit-movements-guarantee-balance.port"                                       -> "101010",
        "microservice.services.transit-movements-guarantee-balance.path"                                       -> "/bar/baz/quu",
        "microservice.services.transit-movements-guarantee-balance.circuit-breaker.max-failures"               -> "5",
        "microservice.services.transit-movements-guarantee-balance.circuit-breaker.call-timeout"               -> "20 seconds",
        "microservice.services.transit-movements-guarantee-balance.circuit-breaker.reset-timeout"              -> "1 second",
        "microservice.services.transit-movements-guarantee-balance.circuit-breaker.max-reset-timeout"          -> "30 seconds",
        "microservice.services.transit-movements-guarantee-balance.circuit-breaker.exponential-backoff-factor" -> "1.5",
        "microservice.services.transit-movements-guarantee-balance.circuit-breaker.random-factor"              -> "0.1"
      )
    )

    appConfig.backendUrl shouldBe AbsoluteUrl.parse("https://foo:101010/bar/baz/quu")

    appConfig.backendCircuitBreakerConfig shouldBe CircuitBreakerConfig(
      maxFailures = 5,
      callTimeout = 20.seconds,
      resetTimeout = 1.second,
      maxResetTimeout = 30.seconds,
      exponentialBackoffFactor = 1.5,
      randomFactor = 0.1
    )
  }

  it should "deserialize features config" in {
    val appConfig = mkAppConfig(
      Configuration(
        "features.fooBar" -> "true",
        "features.bazQuu" -> "false"
      )
    )

    appConfig.features shouldBe Map("fooBar" -> true, "bazQuu" -> false)
  }

  it should "recognise async balance response config" in {
    val appConfigCheckEnabled =
      mkAppConfig(Configuration("features.async-balance-response" -> "true"))

    appConfigCheckEnabled.asyncBalanceResponse shouldBe true

    val appConfigCheckDisabled =
      mkAppConfig(Configuration("features.async-balance-response" -> "false"))

    appConfigCheckDisabled.asyncBalanceResponse shouldBe false

    val appConfigCheckImplicitlyDisabled =
      mkAppConfig(Configuration())

    appConfigCheckImplicitlyDisabled.asyncBalanceResponse shouldBe false
  }
}
