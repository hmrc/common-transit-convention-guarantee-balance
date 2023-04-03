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

package v2.services

import cats.data.EitherT
import cats.effect.IO
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import play.api.http.Status.FORBIDDEN
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.RouterConnector
import v2.models.BalanceRequest
import v2.models.GuaranteeReferenceNumber
import v2.models.InternalBalanceResponse
import v2.models.errors.RoutingError

@ImplementedBy(classOf[RouterServiceImpl])
trait RouterService {

  def request(grn: GuaranteeReferenceNumber, request: BalanceRequest)(implicit hc: HeaderCarrier): EitherT[IO, RoutingError, InternalBalanceResponse]

}

class RouterServiceImpl @Inject() (routerConnector: RouterConnector) extends RouterService {

  override def request(grn: GuaranteeReferenceNumber, request: BalanceRequest)(implicit hc: HeaderCarrier): EitherT[IO, RoutingError, InternalBalanceResponse] =
    EitherT {
      for {
        response  <- routerConnector.get(grn)
        converted <- convertException(response)
      } yield converted
    }

  private def convertException(result: Either[Throwable, InternalBalanceResponse]): IO[Either[RoutingError, InternalBalanceResponse]] =
    IO {
      result match {
        case Right(internalBalanceResponse)                  => Right(internalBalanceResponse)
        case Left(UpstreamErrorResponse(_, FORBIDDEN, _, _)) => Left(RoutingError.InvalidAccessCode)
        case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => Left(RoutingError.GuaranteeReferenceNotFound)
        case Left(ex)                                        => Left(RoutingError.Unexpected(Some(ex)))
      }
    }
}
