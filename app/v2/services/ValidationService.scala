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
import cats.data.NonEmptyList
import cats.data.Validated
import cats.implicits.catsSyntaxTuple2Semigroupal
import cats.implicits.toFunctorOps
import com.google.inject.ImplementedBy
import models.request.AuthenticatedRequest
import play.api.http.HeaderNames
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier
import v2.models.AccessCode
import v2.models.BalanceRequest
import v2.models.GuaranteeReferenceNumber
import v2.models.errors.PresentationError
import v2.models.errors.ValidationError

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[ValidationServiceImpl])
trait ValidationService {

  def validate(payload: BalanceRequest)(implicit ec: ExecutionContext): EitherT[Future, NonEmptyList[ValidationError], Unit]

  def validateAcceptHeader(
    grn: GuaranteeReferenceNumber
  )(implicit request: AuthenticatedRequest[JsValue], hc: HeaderCarrier, ec: ExecutionContext): Either[PresentationError, Unit]
}

class ValidationServiceImpl @Inject() (auditService: AuditService) extends ValidationService {

  private val AcceptHeaderRegex = """application/vnd\.hmrc\.(2.0)\+json""".r

  private def exactLength(value: String, length: Int, error: Int => ValidationError): Validated[NonEmptyList[ValidationError], Unit] =
    Validated.condNel(value.length == length, (), error(length))

  private def alphanumeric(value: String, error: => ValidationError): Validated[NonEmptyList[ValidationError], Unit] =
    Validated.condNel(value.forall(_.isLetterOrDigit), (), error)

  private def validateAccessCode(code: AccessCode): Validated[NonEmptyList[ValidationError], Unit] =
    (
      exactLength(code.value, 4, _ => ValidationError.InvalidAccessCodeLength(code)),
      alphanumeric(code.value, ValidationError.InvalidAccessCodeCharacters(code))
    ).tupled.void

  override def validate(payload: BalanceRequest)(implicit ec: ExecutionContext): EitherT[Future, NonEmptyList[ValidationError], Unit] = EitherT {
    Future {
      validateAccessCode(payload.accessCode).toEither
    }
  }

  def validateAcceptHeader(grn: GuaranteeReferenceNumber)(implicit
    request: AuthenticatedRequest[JsValue],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Either[PresentationError, Unit] =
    request.headers.get(HeaderNames.ACCEPT) match {
      case Some(AcceptHeaderRegex(_)) => Right(())
      case _ =>
        auditService.invalidPayloadBalanceRequest(request, grn)
        Left(PresentationError.notAcceptableError("The accept header must be set to application/vnd.hmrc.2.0+json to use this resource."))
    }
}
