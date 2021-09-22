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
import models.backend.BalanceRequestResponse
import models.backend.PendingBalanceRequest
import models.request.BalanceRequest
import models.values.BalanceId
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse

case class FakeBalanceRequestConnector(
  getRequestResponse: IO[Option[Either[UpstreamErrorResponse, PendingBalanceRequest]]] = IO.stub,
  sendRequestResponse: IO[
    Either[UpstreamErrorResponse, Either[BalanceId, BalanceRequestResponse]]
  ] = IO.stub
) extends BalanceRequestConnector {

  override def getRequest(balanceId: BalanceId)(implicit
    hc: HeaderCarrier
  ): IO[Option[Either[UpstreamErrorResponse, PendingBalanceRequest]]] =
    getRequestResponse

  override def sendRequest(request: BalanceRequest)(implicit
    hc: HeaderCarrier
  ): IO[Either[UpstreamErrorResponse, Either[BalanceId, BalanceRequestResponse]]] =
    sendRequestResponse
}
