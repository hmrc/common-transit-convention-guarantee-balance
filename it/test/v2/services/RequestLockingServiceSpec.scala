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

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import config.AppConfig
import models.values.InternalId
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Configuration
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.lock.Lock
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import v2.models.GuaranteeReferenceNumber
import v2.models.errors.RequestLockingError

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class RequestLockingServiceSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaFutures
    with DefaultPlayMongoRepositorySupport[Lock]
    with ScalaCheckDrivenPropertyChecks {

  implicit val ec: ExecutionContext = ExecutionContext.global

  def mkAppConfig(config: Configuration): AppConfig = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  val timestampSupport                = new CurrentTimestampSupport
  val repository: MongoLockRepository = new MongoLockRepository(mongoComponent, timestampSupport)

  val service = new RequestLockingServiceImpl(
    repository,
    mkAppConfig(Configuration("balance-request.lockout-ttl" -> "250 milliseconds"))
  )

  "BalanceRequestLockService" should "take the lock for a GRN when called without any previous activity" in forAll(
    Gen.stringOfN(5, Gen.alphaNumChar).map(InternalId(_)),
    Gen.stringOfN(18, Gen.alphaNumChar).map(GuaranteeReferenceNumber(_))
  ) {
    (internalId, grn) =>
      service.lock(grn, internalId).value.unsafeToFuture().futureValue shouldBe Right(())
  }

  it should "allow the same user to take the lock for two different GRNs within the timeout period" in forAll(
    Gen.stringOfN(5, Gen.alphaNumChar).map(InternalId(_)),
    Gen.stringOfN(18, Gen.alphaNumChar).map(GuaranteeReferenceNumber(_)),
    Gen.stringOfN(18, Gen.alphaNumChar).map(GuaranteeReferenceNumber(_))
  ) {
    (internalId, grn1, grn2) =>
      val assertion = for {
        first  <- service.lock(grn1, internalId).value
        _      <- IO.sleep(50.millis)
        second <- service.lock(grn2, internalId).value
      } yield (first, second)

      assertion.unsafeToFuture().futureValue.shouldBe((Right(()), Right(())))
  }

  it should "allow two different users to take the lock for the same GRN within the timeout period" in forAll(
    Gen.stringOfN(5, Gen.alphaNumChar).map(InternalId(_)),
    Gen.stringOfN(5, Gen.alphaNumChar).map(InternalId(_)),
    Gen.stringOfN(18, Gen.alphaNumChar).map(GuaranteeReferenceNumber(_))
  ) {
    (internalId1, internalId2, grn) =>
      val assertion = for {
        first  <- service.lock(grn, internalId1).value
        _      <- IO.sleep(50.millis)
        second <- service.lock(grn, internalId2).value
      } yield (first, second)

      assertion.unsafeToFuture().futureValue.shouldBe((Right(()), Right(())))
  }

  it should "deny the second lock acquisition attempt for the same GRN within the timeout period" in forAll(
    Gen.stringOfN(5, Gen.alphaNumChar).map(InternalId(_)),
    Gen.stringOfN(18, Gen.alphaNumChar).map(GuaranteeReferenceNumber(_))
  ) {
    (internalId, grn) =>
      val assertion = for {
        first  <- service.lock(grn, internalId).value
        _      <- IO.sleep(50.millis)
        second <- service.lock(grn, internalId).value
      } yield (first, second)

      assertion.unsafeToFuture().futureValue.shouldBe((Right(()), Left(RequestLockingError.AlreadyLocked)))
  }

  it should "allow the lock to be acquired again for a given GRN after the timeout period" in forAll(
    Gen.stringOfN(5, Gen.alphaNumChar).map(InternalId(_)),
    Gen.stringOfN(18, Gen.alphaNumChar).map(GuaranteeReferenceNumber(_))
  ) {
    (internalId, grn) =>
      val assertion = for {
        first  <- service.lock(grn, internalId).value
        _      <- IO.sleep(300.millis)
        second <- service.lock(grn, internalId).value
      } yield (first, second)

      assertion.unsafeToFuture().futureValue.shouldBe((Right(()), Right(())))
  }
}
