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

package metrics

import cats.effect.IO
import cats.effect.kernel.Deferred
import cats.effect.kernel.Resource
import cats.effect.kernel.Resource.ExitCase
import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import play.api.mvc.Result

trait IOMetrics {
  def metrics: Metrics

  private lazy val registry: MetricRegistry = metrics.defaultRegistry

  class MetricsTimer private[metrics] (metricKey: String, timerCompleted: Deferred[IO, Boolean]) {
    val timerContext: Timer.Context = registry.timer(s"$metricKey-timer").time()
    val successCounter: Counter     = registry.counter(s"$metricKey-success-counter")
    val failureCounter: Counter     = registry.counter(s"$metricKey-failed-counter")

    def completeWithSuccess(): IO[Unit] =
      timerCompleted
        .complete(false)
        .ifM(
          // First call to complete this deferred value
          ifTrue = IO {
            timerContext.stop()
            successCounter.inc()
          },
          // The deferred value was already completed
          ifFalse = IO.unit
        )

    def completeWithFailure(): IO[Unit] =
      timerCompleted
        .complete(false)
        .ifM(
          // First call to complete this deferred value
          ifTrue = IO {
            timerContext.stop()
            failureCounter.inc()
          },
          // The deferred value was already completed
          ifFalse = IO.unit
        )
  }

  def withMetricsTimer[A](metricKey: String)(block: MetricsTimer => IO[A]): IO[A] =
    timerResource(metricKey).use(block)

  def withMetricsTimerResponse[L, R](
    metricKey: String
  )(block: IO[Either[L, R]]): IO[Either[L, R]] =
    timerResource(metricKey).use {
      timer =>
        block.flatMap {
          result =>
            val completeTimer =
              if (result.isLeft)
                timer.completeWithFailure()
              else
                timer.completeWithSuccess()

            completeTimer.as(result)
        }
    }

  def withMetricsTimerResult(metricKey: String)(block: IO[Result]): IO[Result] =
    timerResource(metricKey).use {
      timer =>
        block.flatMap {
          result =>
            val completeTimer =
              if (isFailureStatus(result.header.status))
                timer.completeWithFailure()
              else
                timer.completeWithSuccess()

            completeTimer.as(result)
        }
    }

  private def timerResource(metricKey: String): Resource[IO, MetricsTimer] = {
    val acquireTimer = for {
      deferred <- IO.deferred[Boolean]
      timer    <- IO(new MetricsTimer(metricKey, deferred))
    } yield timer

    Resource.makeCase(acquireTimer) {
      case (timer, ExitCase.Succeeded) =>
        timer.completeWithSuccess()
      case (timer, ExitCase.Errored(_)) =>
        timer.completeWithFailure()
      case (timer, ExitCase.Canceled) =>
        timer.completeWithFailure()
    }
  }

  private def isFailureStatus(status: Int) =
    status / 100 >= 4
}
