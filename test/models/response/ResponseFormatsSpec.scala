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

package models.response

import controllers.routes.BalanceRequestController
import models.values.BalanceId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

import java.util.UUID

class ResponseFormatsSpec extends AnyFlatSpec with Matchers {
  "Link.linkWrites" should "write link as a HAL JSON link" in {
    val uuid  = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val route = BalanceRequestController.getBalanceRequest(BalanceId(uuid))
    val link  = Link(route)
    Json.toJsObject(link) shouldBe Json.obj(
      "href" -> "/customs/guarantees/balances/22b9899e-24ee-48e6-a189-97d1f45391c4"
    )
  }

  "HalResponse.halResponseWrites" should "write a balance request response" in {
    val uuid     = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val response = PostBalanceRequestResponse(BalanceId(uuid))
    Json.toJsObject(response) shouldBe Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj(
          "href" -> "/customs/guarantees/balances/22b9899e-24ee-48e6-a189-97d1f45391c4"
        )
      ),
      "balanceId" -> "22b9899e-24ee-48e6-a189-97d1f45391c4"
    )
  }
}
