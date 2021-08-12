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

package connectors

import cats.effect.IO
import com.google.inject.ImplementedBy
import config.AppConfig
import models.request.BalanceRequest
import models.values.BalanceId
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import runtime.IOFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.UpstreamErrorResponse

import javax.inject.Inject
import javax.inject.Singleton

@ImplementedBy(classOf[BalanceRequestConnectorImpl])
trait BalanceRequestConnector {
  def sendRequest(request: BalanceRequest)(implicit
    hc: HeaderCarrier
  ): IO[Either[UpstreamErrorResponse, BalanceId]]
}

@Singleton
class BalanceRequestConnectorImpl @Inject() (appConfig: AppConfig, http: HttpClient)
    extends BalanceRequestConnector
    with IOFutures {

  def sendRequest(request: BalanceRequest)(implicit
    hc: HeaderCarrier
  ): IO[Either[UpstreamErrorResponse, BalanceId]] =
    IO.runFuture { implicit ec =>
      val url = appConfig.backendUrl.addPathPart("balances")
      val headers = Seq(
        HeaderNames.ACCEPT       -> ContentTypes.JSON,
        HeaderNames.CONTENT_TYPE -> ContentTypes.JSON
      )
      http.POST[BalanceRequest, Either[UpstreamErrorResponse, BalanceId]](
        url.toString,
        request,
        headers
      )
    }
}