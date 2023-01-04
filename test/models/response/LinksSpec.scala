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

package models.response

import controllers.routes.BalanceRequestController
import io.lemonlabs.uri.RelativeUrl
import models.values.BalanceId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class LinksSpec extends AnyFlatSpec with Matchers {
  "Link" should "render routes with context" in {
    val uuid  = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val route = BalanceRequestController.getBalanceRequest(BalanceId(uuid))
    Link(route) shouldBe Link(
      RelativeUrl.parse("/customs/guarantees/balances/22b9899e-24ee-48e6-a189-97d1f45391c4")
    )
  }

  "Link.self" should "produce a HAL self link" in {
    val uuid  = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val route = BalanceRequestController.getBalanceRequest(BalanceId(uuid))
    Link.self(route) shouldBe ("self" -> Link(
      RelativeUrl.parse("/customs/guarantees/balances/22b9899e-24ee-48e6-a189-97d1f45391c4")
    ))
  }

  "Link.named" should "produce a HAL named link" in {
    val uuid  = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val route = BalanceRequestController.getBalanceRequest(BalanceId(uuid))
    Link.named("foo", route) shouldBe ("foo" -> Link(
      RelativeUrl.parse("/customs/guarantees/balances/22b9899e-24ee-48e6-a189-97d1f45391c4")
    ))
  }
}
