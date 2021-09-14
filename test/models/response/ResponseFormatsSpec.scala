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

import cats.data.NonEmptyList
import controllers.routes.BalanceRequestController
import models.backend._
import models.backend.errors.FunctionalError
import models.values._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

import java.util.UUID
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ResponseFormatsSpec extends AnyFlatSpec with Matchers {
  "Link.linkWrites" should "write link as a HAL JSON link" in {
    val uuid  = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val route = BalanceRequestController.getBalanceRequest(BalanceId(uuid))
    val link  = Link(route)
    Json.toJsObject(link) shouldBe Json.obj(
      "href" -> "/customs/guarantees/balances/22b9899e-24ee-48e6-a189-97d1f45391c4"
    )
  }

  "HalResponse.halResponseWrites" should "write a pending balance request POST response" in {
    val uuid         = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val postResponse = PostBalanceRequestPendingResponse(BalanceId(uuid))

    Json.toJsObject(postResponse) shouldBe Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj(
          "href" -> "/customs/guarantees/balances/22b9899e-24ee-48e6-a189-97d1f45391c4"
        )
      ),
      "balanceId" -> "22b9899e-24ee-48e6-a189-97d1f45391c4"
    )
  }

  it should "write a successful balance request POST response" in {
    val balanceRequestSuccess =
      BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP"))
    val postResponse =
      PostBalanceRequestSuccessResponse(balanceRequestSuccess)

    Json.toJsObject(postResponse) shouldBe Json.obj(
      "response" -> Json.obj(
        "balance"  -> 12345678.9,
        "currency" -> "GBP"
      )
    )
  }

  it should "write a functional error POST response" in {
    val balanceRequestFunctionalError =
      BalanceRequestFunctionalError(
        NonEmptyList.one(FunctionalError(ErrorType(14), "Foo.Bar(1).Baz", None))
      )
    val postResponse =
      PostBalanceRequestFunctionalErrorResponse(balanceRequestFunctionalError)

    Json.toJsObject(postResponse) shouldBe Json.obj(
      "code"    -> "FUNCTIONAL_ERROR",
      "message" -> "The request was rejected by the guarantee management system",
      "response" -> Json.obj(
        "errors" -> Json.arr(
          Json.obj(
            "errorType"    -> 14,
            "errorPointer" -> "Foo.Bar(1).Baz"
          )
        )
      )
    )
  }

  it should "write a pending balance request GET response" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val pendingBalanceRequest = PendingBalanceRequest(
      balanceId,
      EnrolmentId("12345678ABC"),
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 52, 15), ZoneOffset.UTC).toInstant,
      completedAt = None,
      response = None
    )

    val getBalanceRequestResponse =
      GetBalanceRequestResponse(balanceId, pendingBalanceRequest)

    Json.toJsObject(getBalanceRequestResponse) shouldBe Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj(
          "href" -> "/customs/guarantees/balances/22b9899e-24ee-48e6-a189-97d1f45391c4"
        )
      ),
      "request" -> Json.obj(
        "balanceId"          -> "22b9899e-24ee-48e6-a189-97d1f45391c4",
        "enrolmentId"        -> "12345678ABC",
        "taxIdentifier"      -> "GB12345678900",
        "guaranteeReference" -> "05DE3300BE0001067A001017",
        "requestedAt"        -> "2021-09-14T09:52:15Z"
      )
    )
  }

  it should "write a successful balance request GET response" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val balanceRequestSuccess =
      BalanceRequestSuccess(BigDecimal("12345678.90"), CurrencyCode("GBP"))

    val pendingBalanceRequest = PendingBalanceRequest(
      balanceId,
      EnrolmentId("12345678ABC"),
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 52, 15), ZoneOffset.UTC).toInstant,
      completedAt = Some(
        OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 53, 5), ZoneOffset.UTC).toInstant
      ),
      response = Some(balanceRequestSuccess)
    )

    val getResponse =
      GetBalanceRequestResponse(balanceId, pendingBalanceRequest)

    Json.toJsObject(getResponse) shouldBe Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj(
          "href" -> "/customs/guarantees/balances/22b9899e-24ee-48e6-a189-97d1f45391c4"
        )
      ),
      "request" -> Json.obj(
        "balanceId"          -> "22b9899e-24ee-48e6-a189-97d1f45391c4",
        "enrolmentId"        -> "12345678ABC",
        "taxIdentifier"      -> "GB12345678900",
        "guaranteeReference" -> "05DE3300BE0001067A001017",
        "requestedAt"        -> "2021-09-14T09:52:15Z",
        "completedAt"        -> "2021-09-14T09:53:05Z",
        "response" -> Json.obj(
          "status"   -> "SUCCESS",
          "balance"  -> 12345678.9,
          "currency" -> "GBP"
        )
      )
    )
  }

  it should "write a functional error GET response" in {
    val uuid      = UUID.fromString("22b9899e-24ee-48e6-a189-97d1f45391c4")
    val balanceId = BalanceId(uuid)

    val balanceRequestFunctionalError =
      BalanceRequestFunctionalError(
        NonEmptyList.one(FunctionalError(ErrorType(14), "Foo.Bar(1).Baz", None))
      )

    val pendingBalanceRequest = PendingBalanceRequest(
      balanceId,
      EnrolmentId("12345678ABC"),
      TaxIdentifier("GB12345678900"),
      GuaranteeReference("05DE3300BE0001067A001017"),
      OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 52, 15), ZoneOffset.UTC).toInstant,
      completedAt = Some(
        OffsetDateTime.of(LocalDateTime.of(2021, 9, 14, 9, 53, 5), ZoneOffset.UTC).toInstant
      ),
      response = Some(balanceRequestFunctionalError)
    )

    val getResponse =
      GetBalanceRequestResponse(balanceId, pendingBalanceRequest)

    Json.toJsObject(getResponse) shouldBe Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj(
          "href" -> "/customs/guarantees/balances/22b9899e-24ee-48e6-a189-97d1f45391c4"
        )
      ),
      "request" -> Json.obj(
        "balanceId"          -> "22b9899e-24ee-48e6-a189-97d1f45391c4",
        "enrolmentId"        -> "12345678ABC",
        "taxIdentifier"      -> "GB12345678900",
        "guaranteeReference" -> "05DE3300BE0001067A001017",
        "requestedAt"        -> "2021-09-14T09:52:15Z",
        "completedAt"        -> "2021-09-14T09:53:05Z",
        "response" -> Json.obj(
          "status" -> "FUNCTIONAL_ERROR",
          "errors" -> Json.arr(
            Json.obj(
              "errorType"    -> 14,
              "errorPointer" -> "Foo.Bar(1).Baz"
            )
          )
        )
      )
    )
  }
}
