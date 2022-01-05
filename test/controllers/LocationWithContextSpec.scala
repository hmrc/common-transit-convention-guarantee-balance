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

package controllers

import controllers.routes.BalanceRequestController
import models.values.BalanceId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class LocationWithContextSpec extends AnyFlatSpec with Matchers with LocationWithContext {
  "Call#pathWithContext" should "produce a path string prefixed with the API context" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    BalanceRequestController.submitBalanceRequest.pathWithContext shouldBe "/customs/guarantees/balances"

    BalanceRequestController
      .getBalanceRequest(balanceId)
      .pathWithContext shouldBe "/customs/guarantees/balances/22b9899e-24ee-48e6-a189-97d1f45391c4"
  }
}
