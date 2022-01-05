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
import com.google.inject.ImplementedBy
import config.AppConfig
import models.values.GuaranteeReference
import models.values.InternalId
import runtime.IOFutures
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject

@ImplementedBy(classOf[BalanceRequestLockServiceImpl])
trait BalanceRequestLockService {
  def isLockedOut(grn: GuaranteeReference, internalId: InternalId): IO[Boolean]
}

class BalanceRequestLockServiceImpl @Inject() (lockRepo: MongoLockRepository, appConfig: AppConfig)
  extends BalanceRequestLockService
  with IOFutures {

  def isLockedOut(grn: GuaranteeReference, internalId: InternalId): IO[Boolean] = IO.runFuture {
    implicit ec =>
      val hash          = MessageDigest.getInstance("SHA-256")
      val lockKey       = internalId.value + grn.value
      val lockHashBytes = hash.digest(lockKey.getBytes(StandardCharsets.UTF_8))
      val lockHashHex   = for (hashByte <- lockHashBytes) yield f"$hashByte%02x"

      lockRepo
        .takeLock(
          lockId = lockHashHex.mkString,
          owner = internalId.value,
          ttl = appConfig.balanceRequestLockoutTtl
        )
        .map(lockTaken => !lockTaken)
  }
}
