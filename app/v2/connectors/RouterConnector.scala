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

import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import config.AppConfig
import config.CircuitBreakerConfig
import connectors.CircuitBreakers
import metrics.MetricsKeys
import org.apache.pekko.stream.Materializer
import play.api.Logging
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables
import uk.gov.hmrc.ctcguaranteebalancerouter.metrics.HasMetrics
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.BalanceRequest
import v2.models.GuaranteeReferenceNumber
import v2.models.InternalBalanceResponse
import v2.models.errors.UpstreamError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

@ImplementedBy(classOf[RouterConnectorImpl])
trait RouterConnector {

  def post(grn: GuaranteeReferenceNumber, balanceRequest: BalanceRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[Throwable, InternalBalanceResponse]]
}

class RouterConnectorImpl @Inject() (appConfig: AppConfig, httpClientV2: HttpClientV2, val metrics: Metrics)(implicit
  val materializer: Materializer
) extends RouterConnector
    with Logging
    with CircuitBreakers
    with HasMetrics
    with JsonBodyWritables {

  override val circuitBreakerConfig: CircuitBreakerConfig = appConfig.routerCircuitBreakerConfig

  override def post(grn: GuaranteeReferenceNumber, balanceRequest: BalanceRequest)(implicit
    hc: HeaderCarrier,
    executionContext: ExecutionContext
  ): Future[Either[Throwable, InternalBalanceResponse]] = {
    val url = appConfig.routerUrl.addPathPart(grn.value).addPathPart("balance")

    withMetricsTimerResponse(MetricsKeys.Connectors.RouterRequest) {
      circuitBreaker.withCircuitBreaker(
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
