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

package services

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import config.AppConfig
import models.values.GuaranteeReference
import models.values.InternalId
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.lock.Lock
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class BalanceRequestLockServiceSpec extends AnyFlatSpec with Matchers with ScalaFutures with DefaultPlayMongoRepositorySupport[Lock] {

  implicit val ec = ExecutionContext.global

  def mkAppConfig(config: Configuration) = {
    val servicesConfig = new ServicesConfig(config)
    new AppConfig(config, servicesConfig)
  }

  val timestampSupport = new CurrentTimestampSupport
  val repository       = new MongoLockRepository(mongoComponent, timestampSupport)

  val service = new BalanceRequestLockServiceImpl(
    repository,
    mkAppConfig(Configuration("balance-request.lockout-ttl" -> "250 milliseconds"))
  )

  "BalanceRequestLockService" should "take the lock for a GRN when called without any previous activity" in {
    val internalId = InternalId("12345")
    val grn        = GuaranteeReference("05DE3300BE0001067A001017")
    service.isLockedOut(grn, internalId).unsafeToFuture().futureValue shouldBe false
  }

  it should "allow the same user to take the lock for two different GRNs within the timeout period" in {
    val internalId = InternalId("12345")
    val grn1       = GuaranteeReference("05DE3300BE0001067A001017")
    val grn2       = GuaranteeReference("20GB0000010000GX1")

    val assertion = for {
      first  <- service.isLockedOut(grn1, internalId)
      _      <- IO.sleep(50.millis)
      second <- service.isLockedOut(grn2, internalId)
    } yield (first, second)

    assertion.unsafeToFuture().futureValue.shouldBe((false, false))
  }

  it should "allow two different users to take the lock for the same GRN within the timeout period" in {
    val internalId1 = InternalId("12345")
    val internalId2 = InternalId("98765")
    val grn         = GuaranteeReference("05DE3300BE0001067A001017")

    val assertion = for {
      first  <- service.isLockedOut(grn, internalId1)
      _      <- IO.sleep(50.millis)
      second <- service.isLockedOut(grn, internalId2)
    } yield (first, second)

    assertion.unsafeToFuture().futureValue.shouldBe((false, false))
  }

  it should "deny the second lock acquisition attempt for the same GRN within the timeout period" in {
    val internalId = InternalId("12345")
    val grn        = GuaranteeReference("05DE3300BE0001067A001017")

    val assertion = for {
      first  <- service.isLockedOut(grn, internalId)
      _      <- IO.sleep(50.millis)
      second <- service.isLockedOut(grn, internalId)
    } yield (first, second)

    assertion.unsafeToFuture().futureValue.shouldBe((false, true))
  }

  it should "allow the lock to be acquired again for a given GRN after the timeout period" in {
    val internalId = InternalId("12345")
    val grn        = GuaranteeReference("05DE3300BE0001067A001017")

    val assertion = for {
      first  <- service.isLockedOut(grn, internalId)
      _      <- IO.sleep(300.millis)
      second <- service.isLockedOut(grn, internalId)
    } yield (first, second)

    assertion.unsafeToFuture().futureValue.shouldBe((false, false))
  }
}
