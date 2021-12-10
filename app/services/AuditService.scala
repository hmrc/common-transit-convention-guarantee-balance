/*
 * Copyright 2021 HM Revenue & Customs
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
import models.audit.AuditEventType
import models.audit.RateLimitedRequestEvent
import models.request.AuthenticatedRequest
import models.request.BalanceRequest
import play.api.libs.json.JsValue
import runtime.IOFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import javax.inject.Inject
import javax.inject.Singleton

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {
  def auditRateLimitedRequest(
    request: AuthenticatedRequest[JsValue],
    balanceRequest: BalanceRequest
  )(implicit
    hc: HeaderCarrier
  ): IO[Unit]
}

@Singleton
class AuditServiceImpl @Inject() (connector: AuditConnector) extends AuditService with IOFutures {

  override def auditRateLimitedRequest(
    request: AuthenticatedRequest[JsValue],
    balanceRequest: BalanceRequest
  )(implicit
    hc: HeaderCarrier
  ): IO[Unit] = {
    val rateLimitedRequestEvent = RateLimitedRequestEvent.fromRequest(request, balanceRequest)

    IO.executionContext.flatMap { implicit ec =>
      IO {
        connector.sendExplicitAudit[RateLimitedRequestEvent](
          AuditEventType.RateLimitedRequest.name,
          rateLimitedRequestEvent
        )
      }
    }
  }
}
