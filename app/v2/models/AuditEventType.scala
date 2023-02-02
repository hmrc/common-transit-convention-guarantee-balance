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

package v2.models

sealed abstract class AuditEventType(val name: String) extends Product with Serializable

object AuditEventType {
  case object BalanceRequested        extends AuditEventType("BalanceRequested")
  case object RateLimited             extends AuditEventType("RateLimited")
  case object InvalidPayload          extends AuditEventType("InvalidPayload")
  case object GRNNotFound             extends AuditEventType("GRNNotFound")
  case object AccessCodeNotValid      extends AuditEventType("AccessCodeNotValid")
  case object ServerError             extends AuditEventType("ServerError")
  case object BalanceRequestSucceeded extends AuditEventType("BalanceRequestSucceeded")
}