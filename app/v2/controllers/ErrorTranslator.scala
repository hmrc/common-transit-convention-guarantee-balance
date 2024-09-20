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

package v2.controllers

import cats.data.EitherT
import cats.data.NonEmptyList
import cats.effect.IO
import uk.gov.hmrc.http.HeaderCarrier
import v2.models.AuditInfo
import v2.models.errors._
import v2.services.AuditService

import scala.concurrent.ExecutionContext

trait ErrorTranslator {

  implicit class ErrorConverter[E, A](value: EitherT[IO, E, A]) {

    def asPresentation(auditInfo: AuditInfo, auditService: AuditService)(implicit
      c: Converter[E],
      headerCarrier: HeaderCarrier,
      ec: ExecutionContext
    ): EitherT[IO, PresentationError, A] = {
      implicit val info: AuditInfo = auditInfo
      value.leftMap {
        error =>
          auditService.balanceRequestFailed(error)
          c.convert(error)
      }
    }
  }

  trait Converter[E] {
    def convert(input: E): PresentationError
  }

  implicit val validationErrorConverter: Converter[NonEmptyList[ValidationError]] = (validationError: NonEmptyList[ValidationError]) =>
    validationError.head match {
      case ValidationError.InvalidAccessCodeLength(accessCode) =>
        PresentationError.badRequestError(s"Access code ${accessCode.value} must be four alphanumeric characters.")
      case ValidationError.InvalidAccessCodeCharacters(accessCode) =>
        PresentationError.badRequestError(s"Access code ${accessCode.value} must be four alphanumeric characters.")
    }

  implicit val requestLockingErrorConverter: Converter[RequestLockingError] = {
    case RequestLockingError.AlreadyLocked   => PresentationError.rateLimitedRequest()
    case RequestLockingError.Unexpected(thr) => PresentationError.internalServiceError(cause = thr)
  }

  implicit val routingErrorConverter: Converter[RoutingError] = new Converter[RoutingError] {

    private val notFoundError = "The guarantee reference number or access code did not match an existing guarantee."

    override def convert(input: RoutingError): PresentationError = input match {
      case RoutingError.GuaranteeReferenceNotFound => PresentationError.notFoundError(notFoundError)
      case RoutingError.InvalidAccessCode          => PresentationError.notFoundError(notFoundError)
      case RoutingError.InvalidGuaranteeType       => PresentationError.invalidGuaranteeTypeError("Guarantee type is not supported.")
      case RoutingError.Unexpected(thr)            => PresentationError.internalServiceError(cause = thr)
    }
  }

}
