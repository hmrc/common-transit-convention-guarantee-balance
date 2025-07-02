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
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.FORBIDDEN
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import v2.connectors.RouterConnector
import v2.models.BalanceRequest
import v2.models.GuaranteeReferenceNumber
import v2.models.InternalBalanceResponse
import v2.models.errors.ErrorCode
import v2.models.errors.RoutingError
import v2.models.errors.StandardError
import v2.models.errors.UpstreamError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

@ImplementedBy(classOf[RouterServiceImpl])
trait RouterService {

  def request(grn: GuaranteeReferenceNumber, request: BalanceRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, RoutingError, InternalBalanceResponse]

}

class RouterServiceImpl @Inject() (routerConnector: RouterConnector) extends RouterService {

  override def request(grn: GuaranteeReferenceNumber, request: BalanceRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, RoutingError, InternalBalanceResponse] =
    EitherT {
      for {
        response  <- routerConnector.post(grn, request)
        converted <- convertException(response)
      } yield converted
    }

  private def convertException(
    result: Either[Throwable, InternalBalanceResponse]
  )(implicit ec: ExecutionContext): Future[Either[RoutingError, InternalBalanceResponse]] =
    Future {
      result match {
        case Right(internalBalanceResponse)                 => Right(internalBalanceResponse)
        case Left(UpstreamError(_, FORBIDDEN, _, _))        => Left(RoutingError.InvalidAccessCode)
        case Left(UpstreamError(_, NOT_FOUND, _, _))        => Left(RoutingError.GuaranteeReferenceNotFound)
        case Left(ex @ UpstreamError(_, BAD_REQUEST, _, _)) => determineBadRequest(ex)
        case Left(ex)                                       => Left(RoutingError.Unexpected(Some(ex)))
      }
    }

  private def determineBadRequest(ex: UpstreamError): Left[RoutingError, InternalBalanceResponse] =
    Try {
      if (Json.parse(ex.message).validate[StandardError].map(_.code).contains(ErrorCode.InvalidGuaranteeType)) Left(RoutingError.InvalidGuaranteeType)
      else Left(RoutingError.Unexpected(Some(ex.asUpstreamErrorResponse)))
    }.fold(
      _ => Left(RoutingError.Unexpected(Some(ex.asUpstreamErrorResponse))),
      x => x
    )
}
