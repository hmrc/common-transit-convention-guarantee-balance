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

package connectors

import org.apache.pekko.pattern.CircuitBreaker
import org.apache.pekko.stream.Materializer
import config.CircuitBreakerConfig
import play.api.Logging

import scala.concurrent.duration.*

trait CircuitBreakers { self: Logging =>
  def materializer: Materializer
  def circuitBreakerConfig: CircuitBreakerConfig

  private val clazz = getClass.getSimpleName

  lazy val circuitBreaker: CircuitBreaker = new CircuitBreaker(
    scheduler = materializer.system.scheduler,
    maxFailures = circuitBreakerConfig.maxFailures,
    callTimeout = circuitBreakerConfig.callTimeout,
    resetTimeout = circuitBreakerConfig.resetTimeout,
    maxResetTimeout = circuitBreakerConfig.maxResetTimeout,
    exponentialBackoffFactor = circuitBreakerConfig.exponentialBackoffFactor,
    randomFactor = circuitBreakerConfig.randomFactor
  )(materializer.executionContext)
    .onOpen(logger.error(s"Circuit breaker for $clazz opening due to failures"))
    .onHalfOpen(logger.warn(s"Circuit breaker for $clazz resetting after failures"))
    .onClose {
      logger.warn(s"Circuit breaker for $clazz closing after trial connection success")
    }
    .onCallFailure(
      _ => logger.error(s"Circuit breaker for $clazz recorded failed call")
    )
    .onCallBreakerOpen {
      logger.error(s"Circuit breaker for $clazz rejected call due to previous failures")
    }
    .onCallTimeout {
      elapsed =>
        val duration = Duration.fromNanos(elapsed)
        logger.error(
          s"Circuit breaker for $clazz recorded failed call due to timeout after ${duration.toMillis}ms"
        )
    }
}
