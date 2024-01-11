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

import org.apache.pekko.stream.Materializer
import cats.effect.IO
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import config.AppConfig
import config.CircuitBreakerConfig
import connectors.CircuitBreakers
import logging.Logging
import metrics.IOMetrics
import metrics.MetricsKeys
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.libs.json.Json
import runtime.IOFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.BalanceRequest
import v2.models.GuaranteeReferenceNumber
import v2.models.InternalBalanceResponse
import v2.models.errors.UpstreamError

import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

@ImplementedBy(classOf[RouterConnectorImpl])
trait RouterConnector {

  def post(grn: GuaranteeReferenceNumber, balanceRequest: BalanceRequest)(implicit hc: HeaderCarrier): IO[Either[Throwable, InternalBalanceResponse]]

}

class RouterConnectorImpl @Inject() (appConfig: AppConfig, httpClientV2: HttpClientV2, val metrics: com.codahale.metrics.MetricRegistry)(implicit
  val materializer: Materializer
) extends RouterConnector
    with IOFutures
    with IOMetrics
    with CircuitBreakers
    with Logging {

  override def circuitBreakerConfig: CircuitBreakerConfig = appConfig.routerCircuitBreakerConfig

  override def post(grn: GuaranteeReferenceNumber, balanceRequest: BalanceRequest)(implicit hc: HeaderCarrier): IO[Either[Throwable, InternalBalanceResponse]] =
    withMetricsTimerResponse(MetricsKeys.Connectors.RouterRequest) {
      IO.runFuture {
        implicit ec =>
          circuitBreaker.withCircuitBreaker(
            {
              val url = appConfig.routerUrl.addPathPart(grn.value).addPathPart("balance")

              httpClientV2
                .post(url"$url")
                .withBody(Json.toJson(balanceRequest))
                .setHeader(
                  HeaderNames.ACCEPT        -> ContentTypes.JSON,
                  HeaderNames.CONTENT_TYPE  -> ContentTypes.JSON,
                  HeaderNames.AUTHORIZATION -> appConfig.internalAuthToken
                )
                .execute[HttpResponse]
                .map {
                  response =>
                    response.status match {
                      case x if x <= 399 => Right(response.json.as[InternalBalanceResponse])
                      case _             => Left(UpstreamError(response.body, response.status, response.status, response.headers))
                    }
                }
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
      case Success(Right(_) | Left(_: UpstreamError)) => false
      case _                                          => true
    }

}
