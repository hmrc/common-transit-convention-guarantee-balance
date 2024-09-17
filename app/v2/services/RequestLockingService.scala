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
import config.AppConfig
import v2.models.GuaranteeReferenceNumber
import models.values.InternalId
import runtime.IOFutures
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import v2.models.errors.RequestLockingError

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import scala.util.control.NonFatal

@ImplementedBy(classOf[RequestLockingServiceImpl])
trait RequestLockingService {
  def lock(grn: GuaranteeReferenceNumber, internalId: InternalId): EitherT[IO, RequestLockingError, Unit]
}

class RequestLockingServiceImpl @Inject() (lockRepo: MongoLockRepository, appConfig: AppConfig) extends RequestLockingService with IOFutures {

  // Note to implementors: THIS IS NOT THREAD SAFE. We have to synchronise on this in our IOs below, hence the blocking.
  val hash: MessageDigest = MessageDigest.getInstance("SHA-256")

  override def lock(grn: GuaranteeReferenceNumber, internalId: InternalId): EitherT[IO, RequestLockingError, Unit] =
    EitherT {
      for {
        key    <- IO.pure(internalId.value + grn.value)
        digest <- IO.blocking(hash.synchronized(hash.digest(key.getBytes(StandardCharsets.UTF_8)))) // blocking due to synchronized
        hex <- IO(
          digest.map(
            x => f"$x%02x"
          )
        )
        result <- isLockedOut(hex, internalId)
      } yield result
    }

  private def isLockedOut(lockedHashHex: Array[String], internalId: InternalId): IO[Either[RequestLockingError, Unit]] = IO.runFuture {
    implicit ec =>
      lockRepo
        .takeLock(
          lockId = lockedHashHex.mkString,
          owner = internalId.value,
          ttl = appConfig.balanceRequestLockoutTtl
        )
        .map {
          case false => Left(RequestLockingError.AlreadyLocked)
          case true  => Right(())
        }
        .recover {
          case NonFatal(ex) => Left(RequestLockingError.Unexpected(Some(ex)))
        }
  }
}
