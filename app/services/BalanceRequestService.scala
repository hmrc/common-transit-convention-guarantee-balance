/*
 * Copyright 2022 HM Revenue & Customs
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

package services

import cats.effect.IO
import cats.syntax.all._
import connectors.BalanceRequestConnector
import models.backend.BalanceRequestResponse
import models.backend.PendingBalanceRequest
import models.errors._
import models.request.BalanceRequest
import models.values.BalanceId
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse._

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceRequestService @Inject() (connector: BalanceRequestConnector) {
  def submitBalanceRequest(request: BalanceRequest)(implicit
    hc: HeaderCarrier
  ): IO[Either[BalanceRequestError, Either[BalanceId, BalanceRequestResponse]]] =
    connector.sendRequest(request).map {
      _.leftMap {
        case error @ Upstream4xxResponse(_) =>
          InternalServiceError.causedBy(error)
        case error @ Upstream5xxResponse(_) =>
          UpstreamServiceError.causedBy(error)
      }
    }

  def getBalanceRequest(balanceId: BalanceId)(implicit
    hc: HeaderCarrier
  ): IO[Either[BalanceRequestError, PendingBalanceRequest]] =
    connector.getRequest(balanceId).map {
      case Some(requestOrError) =>
        requestOrError.leftMap {
          case error @ Upstream4xxResponse(_) =>
            InternalServiceError.causedBy(error)
          case error @ Upstream5xxResponse(_) =>
            UpstreamServiceError.causedBy(error)
        }
      case None =>
        Left(BalanceRequestError.notFoundError(balanceId))
    }
}
