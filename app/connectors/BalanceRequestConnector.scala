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
import config.Constants
import models.backend.BalanceRequestResponse
import models.backend.PendingBalanceRequest
import models.request.BalanceRequest
import models.request.Channel
import models.values.BalanceId
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.Status
import runtime.IOFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse

import javax.inject.Inject
import javax.inject.Singleton

@ImplementedBy(classOf[BalanceRequestConnectorImpl])
trait BalanceRequestConnector {
  def sendRequest(request: BalanceRequest)(implicit
    hc: HeaderCarrier
  ): IO[Either[UpstreamErrorResponse, Either[BalanceId, BalanceRequestResponse]]]

  def getRequest(balanceId: BalanceId)(implicit
    hc: HeaderCarrier
  ): IO[Option[Either[UpstreamErrorResponse, PendingBalanceRequest]]]
}

@Singleton
class BalanceRequestConnectorImpl @Inject() (appConfig: AppConfig, http: HttpClient)
    extends BalanceRequestConnector
    with IOFutures {

  implicit val eitherBalanceIdOrResponseReads
    : HttpReads[Either[BalanceId, BalanceRequestResponse]] =
    HttpReads[HttpResponse].map { response =>
      response.status match {
        case Status.ACCEPTED =>
          Left(response.json.as[BalanceId])
        case Status.OK =>
          Right(response.json.as[BalanceRequestResponse])
      }
    }

  def sendRequest(request: BalanceRequest)(implicit
    hc: HeaderCarrier
  ): IO[Either[UpstreamErrorResponse, Either[BalanceId, BalanceRequestResponse]]] =
    IO.runFuture { implicit ec =>
      val url = appConfig.backendUrl.addPathPart("balances")

      val headers = Seq(
        HeaderNames.ACCEPT       -> ContentTypes.JSON,
        HeaderNames.CONTENT_TYPE -> ContentTypes.JSON,
        Constants.ChannelHeader  -> Channel.Api.name
      )

      http.POST[BalanceRequest, Either[
        UpstreamErrorResponse,
        Either[BalanceId, BalanceRequestResponse]
      ]](
        url.toString,
        request,
        headers
      )
    }

  def getRequest(balanceId: BalanceId)(implicit
    hc: HeaderCarrier
  ): IO[Option[Either[UpstreamErrorResponse, PendingBalanceRequest]]] =
    IO.runFuture { implicit ec =>
      val url = appConfig.backendUrl.addPathPart("balances").addPathPart(balanceId.value)

      val headers = Seq(
        HeaderNames.ACCEPT       -> ContentTypes.JSON,
        HeaderNames.CONTENT_TYPE -> ContentTypes.JSON,
        Constants.ChannelHeader  -> Channel.Api.name
      )

      http.GET[Option[Either[UpstreamErrorResponse, PendingBalanceRequest]]](
        url.toString,
        queryParams = Seq.empty,
        headers = headers
      )
    }
}
