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
import com.google.inject.ImplementedBy
import config.AppConfig
import v2.models.GuaranteeReferenceNumber
import models.values.InternalId

import uk.gov.hmrc.mongo.lock.MongoLockRepository
import v2.models.errors.RequestLockingError

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[RequestLockingServiceImpl])
trait RequestLockingService {
  def lock(grn: GuaranteeReferenceNumber, internalId: InternalId)(implicit ec: ExecutionContext): EitherT[Future, RequestLockingError, Unit]
}

class RequestLockingServiceImpl @Inject() (lockRepo: MongoLockRepository, appConfig: AppConfig) extends RequestLockingService {

  // Note to implementors: THIS IS NOT THREAD SAFE. We have to synchronise on this in our IOs below, hence the blocking.
  private val hash: MessageDigest = MessageDigest.getInstance("SHA-256")

  override def lock(grn: GuaranteeReferenceNumber, internalId: InternalId)(implicit ec: ExecutionContext): EitherT[Future, RequestLockingError, Unit] = {
    val key    = internalId.value + grn.value
    val digest = hash.synchronized(hash.digest(key.getBytes(StandardCharsets.UTF_8)))
    val hex    = digest.map(
      x => f"$x%02x"
    )
    EitherT(isLockedOut(hex, internalId))
  }

  private def isLockedOut(lockedHashHex: Array[String], internalId: InternalId)(implicit ec: ExecutionContext): Future[Either[RequestLockingError, Unit]] =
    lockRepo
      .takeLock(
        lockId = lockedHashHex.mkString,
        owner = internalId.value,
        ttl = appConfig.balanceRequestLockoutTtl
      )
      .map {
        case Some(_) => Right(())
        case None    => Left(RequestLockingError.AlreadyLocked)
      }
      .recover {
        case NonFatal(ex) => Left(RequestLockingError.Unexpected(Some(ex)))
      }
}
