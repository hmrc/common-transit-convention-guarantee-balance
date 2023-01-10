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
import cats.effect.unsafe.implicits.global
import org.mockito.MockitoSugar
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import v2.models.AccessCode
import v2.models.errors.InternalServiceError
import v2.models.errors.PresentationError
import v2.models.errors.RequestLockingError
import v2.models.errors.RoutingError
import v2.models.errors.ValidationError

class ErrorTranslatorSpec extends AnyFlatSpec with Matchers with MockitoSugar with ScalaFutures with ScalaCheckDrivenPropertyChecks {

  object Harness extends ErrorTranslator

  import Harness._

  "ErrorConverter#asPresentation" should "for a success return the same right" in {
    val input: EitherT[IO, RequestLockingError, Unit] = EitherT.rightT[IO, RequestLockingError](())
    whenReady(input.asPresentation.value.unsafeToFuture()) {
      _ shouldBe Right(())
    }
  }

  it should "for an error returns a left with the appropriate presentation error" in {
    val error                                         = new IllegalStateException()
    val input: EitherT[IO, RequestLockingError, Unit] = EitherT.leftT[IO, Unit](RequestLockingError.Unexpected(Some(error)))
    whenReady(input.asPresentation.value.unsafeToFuture()) {
      _ shouldBe Left(InternalServiceError(cause = Some(error)))
    }
  }

  "ValidationError" should "return a bad request when any error is provided" in forAll(
    Gen.oneOf(
      NonEmptyList.one(ValidationError.InvalidAccessCodeLength(AccessCode("ABCD?"))),
      NonEmptyList.one(ValidationError.InvalidAccessCodeCharacters(AccessCode("ABCD?"))),
      NonEmptyList.of(
        ValidationError.InvalidAccessCodeCharacters(AccessCode("ABCD?")),
        ValidationError.InvalidAccessCodeLength(AccessCode("ABCD?"))
      )
    )
  ) {
    error =>
      validationErrorConverter.convert(error) shouldBe
        PresentationError.badRequestError(s"Access code ABCD? must be four alphanumeric characters.")
  }

  "RequestLockingError" should "return a rate limited error if we are rate limited" in {
    requestLockingErrorConverter.convert(RequestLockingError.AlreadyLocked) shouldBe
      PresentationError.rateLimited("Too many requests.")
  }

  it should "return an Unexpected if an exception occurs" in {
    val exception = new IllegalStateException()
    requestLockingErrorConverter.convert(RequestLockingError.Unexpected(thr = Some(exception))) shouldBe
      PresentationError.internalServiceError(cause = Some(exception))
  }

  "RoutingError" should "return a not found if we don't find the GRN" in {
    routingErrorConverter.convert(RoutingError.GuaranteeReferenceNotFound) shouldBe
      PresentationError.notFoundError("Guarantee balance not found.")
  }

  it should "return a not found if the access code does not match" in {
    routingErrorConverter.convert(RoutingError.InvalidAccessCode) shouldBe
      PresentationError.notFoundError("Guarantee balance not found.")
  }

  it should "return an Unexpected if an exception occurs" in {
    val exception = new IllegalStateException()
    routingErrorConverter.convert(RoutingError.Unexpected(thr = Some(exception))) shouldBe
      PresentationError.internalServiceError(cause = Some(exception))
  }

}