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
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.implicits.global
import com.codahale.metrics.Counter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import controllers.actions.IOActions
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.endsWith
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers.*
import runtime.IOFutures
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.util.concurrent.CancellationException
import scala.concurrent.Future
import scala.concurrent.duration.*

class IOMetricsSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  class IOMetricsConnector(val metrics: Metrics) extends IOFutures with IOMetrics {

    def okHttpCall: IO[Either[Nothing, Int]] =
      withMetricsTimerResponse("connector-ok") {
        IO.runFuture {
          _ => Future.successful(Right(1))
        }
      }

    def clientErrorHttpCall: IO[Either[UpstreamErrorResponse, Nothing]] =
      withMetricsTimerResponse("connector-client-error") {
        IO.runFuture {
          _ => Future.successful(Left(UpstreamErrorResponse("Arghhh!!!", 400)))
        }
      }

    def serverErrorHttpCall: IO[Either[UpstreamErrorResponse, Nothing]] =
      withMetricsTimerResponse("connector-server-error") {
        IO.runFuture {
          _ => Future.successful(Left(UpstreamErrorResponse("Kaboom!!!", 502)))
        }
      }

    def unhandledExceptionHttpCall: IO[Either[Nothing, Nothing]] =
      withMetricsTimerResponse("connector-unhandled-exception") {
        IO.runFuture {
          _ => Future.failed(new RuntimeException)
        }
      }

    def cancelledHttpCall: IO[Either[Nothing, Int]] =
      withMetricsTimerResponse("connector-unhandled-exception") {
        IO.canceled.as(Right(1))
      }

    def autoCompleteWithSuccessCall: IO[Unit] =
      withMetricsTimer("connector-auto-success")(
        _ => IO.unit
      )

    def autoCompleteWithFailureErrorCall: IO[Int] =
      withMetricsTimer("connector-auto-success")(
        _ => IO.raiseError[Int](new RuntimeException)
      )

    def autoCompleteWithFailureCancelledCall: IO[Unit] =
      withMetricsTimer("connector-auto-success")(
        _ => IO.canceled
      )

    def manualCompleteSuccessCall: IO[Unit] =
      withMetricsTimer("connector-manual-success")(_.completeWithSuccess())

    def manualCompleteFailureCall: IO[Unit] =
      withMetricsTimer("connector-manual-failure")(_.completeWithFailure())
  }

  class IOMetricsController(val metrics: Metrics, val runtime: IORuntime)
      extends BackendController(Helpers.stubControllerComponents())
      with IOActions
      with IOMetrics {

    def okEndpoint: Action[AnyContent] = Action.io {
      withMetricsTimerResult("controller-ok") {
        IO.sleep(50.millis).as(Ok)
      }
    }

    def clientErrorEndpoint: Action[AnyContent] = Action.io {
      withMetricsTimerResult("controller-client-error") {
        IO.sleep(50.millis).as(BadRequest)
      }
    }

    def serverErrorEndpoint: Action[AnyContent] = Action.io {
      withMetricsTimerResult("controller-server-error") {
        IO.sleep(50.millis).as(BadGateway)
      }
    }

    def unhandledExceptionEndpoint: Action[AnyContent] = Action.io {
      withMetricsTimerResult("controller-unhandled-exception") {
        IO.raiseError(new RuntimeException).as(Ok)
      }
    }

    def cancelledEndpoint: Action[AnyContent] = Action.io {
      withMetricsTimerResult("controller-unhandled-exception") {
        IO.canceled.as(Ok)
      }
    }
  }

  val metrics: Metrics            = mock[Metrics]
  val registry: MetricRegistry    = mock[MetricRegistry]
  val timer: Timer                = mock[Timer]
  val timerContext: Timer.Context = mock[Timer.Context]
  val successCounter: Counter     = mock[Counter]
  val failureCounter: Counter     = mock[Counter]

  override protected def beforeEach(): Unit = {
    reset(metrics, registry, timer, timerContext, successCounter, failureCounter)
    when(metrics.defaultRegistry).thenReturn(registry)
    when(registry.timer(any())).thenReturn(timer)
    when(registry.counter(endsWith("success-counter"))).thenReturn(successCounter)
    when(registry.counter(endsWith("failed-counter"))).thenReturn(failureCounter)
    when(timer.time()).thenReturn(timerContext)
  }

  "IOMetrics.withMetricsTimer" should "complete with success when the call completes successfully" in {
    val connector = new IOMetricsConnector(metrics)
    connector.autoCompleteWithSuccessCall.unsafeRunSync()
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(successCounter, times(1)).inc()
  }

  it should "complete with failure when the IO action raises an error" in {
    val connector = new IOMetricsConnector(metrics)
    assertThrows[RuntimeException] {
      connector.autoCompleteWithFailureErrorCall.unsafeRunSync()
    }
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }

  it should "complete with failure when there is an unhandled runtime exception in the HTTP Future call" in {
    val connector = new IOMetricsConnector(metrics)
    assertThrows[RuntimeException] {
      connector.unhandledExceptionHttpCall.unsafeRunSync()
    }
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }

  it should "complete with failure when the IO is cancelled" in {
    val connector = new IOMetricsConnector(metrics)
    assertThrows[CancellationException] {
      connector.cancelledHttpCall.unsafeRunSync()
    }
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }

  it should "complete with success when the user calls completeWithSuccess explicitly" in {
    val connector = new IOMetricsConnector(metrics)
    connector.manualCompleteSuccessCall.unsafeRunSync()
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(successCounter, times(1)).inc()
  }

  it should "complete with failure when the user calls completeWithFailure explicitly" in {
    val connector = new IOMetricsConnector(metrics)
    connector.manualCompleteFailureCall.unsafeRunSync()
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }

  "IOMetrics.withMetricsTimerResponse" should "complete with success when the HTTP response is a success" in {
    val connector = new IOMetricsConnector(metrics)
    connector.okHttpCall.unsafeRunSync()
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(successCounter, times(1)).inc()
  }

  it should "complete with failure when the HTTP response is a client error" in {
    val connector = new IOMetricsConnector(metrics)
    connector.clientErrorHttpCall.unsafeRunSync()
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }

  it should "complete with failure when the HTTP response is a server error" in {
    val connector = new IOMetricsConnector(metrics)
    connector.serverErrorHttpCall.unsafeRunSync()
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }

  it should "complete with failure when the IO action is cancelled" in {
    val connector = new IOMetricsConnector(metrics)
    assertThrows[CancellationException] {
      connector.cancelledHttpCall.unsafeRunSync()
    }
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }

  it should "complete with failure when there is an unhandled runtime exception" in {
    val connector = new IOMetricsConnector(metrics)
    assertThrows[RuntimeException] {
      connector.unhandledExceptionHttpCall.unsafeRunSync()
    }
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }

  "IOMetrics.withMetricsTimerResult" should "complete with success when the result has a successful status code" in {
    val controller = new IOMetricsController(metrics, global)
    val result     = controller.okEndpoint(FakeRequest())
    status(result) shouldBe OK
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(successCounter, times(1)).inc()
  }

  it should "complete with failure when the result has a client error status code" in {
    val controller = new IOMetricsController(metrics, global)
    val result     = controller.clientErrorEndpoint(FakeRequest())
    status(result) shouldBe BAD_REQUEST
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }

  it should "complete with failure when the result has a server error status code" in {
    val controller = new IOMetricsController(metrics, global)
    val result     = controller.serverErrorEndpoint(FakeRequest())
    status(result) shouldBe BAD_GATEWAY
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }

  it should "complete with failure when the server has an unhandled runtime exception" in {
    val controller = new IOMetricsController(metrics, global)
    assertThrows[RuntimeException] {
      await(controller.unhandledExceptionEndpoint(FakeRequest()))
    }
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }

  it should "complete with failure when the request handling IO action is cancelled" in {
    val controller = new IOMetricsController(metrics, global)
    assertThrows[CancellationException] {
      await(controller.cancelledEndpoint(FakeRequest()))
    }
    verify(timer, times(1)).time()
    verify(timerContext, times(1)).stop()
    verify(failureCounter, times(1)).inc()
  }
}
