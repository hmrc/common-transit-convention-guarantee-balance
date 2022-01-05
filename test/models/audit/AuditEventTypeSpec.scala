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

package models.audit

import models.request.AuthenticatedRequest
import models.request.BalanceRequest
import models.values.AccessCode
import models.values.GuaranteeReference
import models.values.InternalId
import models.values.TaxIdentifier
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import play.api.libs.json.JsNull
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.test.FakeRequest

class AuditEventTypeSpec extends AnyFlatSpec {

  val internalId         = InternalId("ABC123")
  val taxIdentifier      = TaxIdentifier("GB12345678900")
  val guaranteeReference = GuaranteeReference("05DE3300BE0001067A001017")
  val accessCode         = AccessCode("1234")

  "RateLimitedRequestEvent" should "serialise to JSON" in {
    val authenticatedRequest: AuthenticatedRequest[JsValue] =
      AuthenticatedRequest(
        FakeRequest().withBody(JsNull), // we don't care here as we have a separate balance request
        internalId
      )

    val balanceRequest = BalanceRequest(taxIdentifier, guaranteeReference, accessCode)

    val event = RateLimitedRequestEvent.fromRequest(authenticatedRequest, balanceRequest)

    Json.toJsObject(event) shouldBe Json.obj(
      "userInternalId"     -> "ABC123",
      "eoriNumber"         -> "GB12345678900",
      "guaranteeReference" -> "05DE3300BE0001067A001017",
      "accessCode"         -> "1234"
    )
  }

}
