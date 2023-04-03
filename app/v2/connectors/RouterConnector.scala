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

package v2.connectors

import akka.stream.Materializer
import cats.effect.IO
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import config.CircuitBreakerConfig
import connectors.CircuitBreakers
import logging.Logging
import metrics.IOMetrics
import metrics.MetricsKeys
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import runtime.IOFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.GuaranteeReferenceNumber
import v2.models.InternalBalanceResponse

import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

@ImplementedBy(classOf[RouterConnectorImpl])
trait RouterConnector {

  def get(grn: GuaranteeReferenceNumber)(implicit hc: HeaderCarrier): IO[Either[Throwable, InternalBalanceResponse]]

}

class RouterConnectorImpl @Inject() (appConfig: AppConfig, httpClientV2: HttpClientV2, val metrics: Metrics)(implicit
  val materializer: Materializer
) extends RouterConnector
    with IOFutures
    with IOMetrics
    with CircuitBreakers
    with Logging {

  override def circuitBreakerConfig: CircuitBreakerConfig = appConfig.routerCircuitBreakerConfig

  override def get(grn: GuaranteeReferenceNumber)(implicit hc: HeaderCarrier): IO[Either[Throwable, InternalBalanceResponse]] =
    withMetricsTimerResponse(MetricsKeys.Connectors.RouterRequest) {
      IO.runFuture {
        implicit ec =>
          circuitBreaker.withCircuitBreaker(
            {
              val url = appConfig.routerUrl.addPathPart("guarantees").addPathPart(grn.value).addPathPart("balance")

              httpClientV2
                .get(url"$url")
                .setHeader(
                  HeaderNames.ACCEPT        -> ContentTypes.JSON,
                  HeaderNames.AUTHORIZATION -> appConfig.internalAuthToken
                )
                .execute[InternalBalanceResponse]
                .map(
                  r => Right[Throwable, InternalBalanceResponse](r)
                )
                .recover {
                  case NonFatal(ex) => Left[Throwable, InternalBalanceResponse](ex)
                }
            },
            defineFailureFn = isFailure
          )
      }
    }

  def isFailure(result: Try[Either[Throwable, InternalBalanceResponse]]): Boolean =
    result match {
      // we can communicate with the backend, so that should handle if EIS/ERMIS is being iffy
      case Success(Right(_) | Left(_: UpstreamErrorResponse)) => false
      case _                                                  => true
    }

}
