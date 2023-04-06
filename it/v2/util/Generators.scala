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

package v2.util

import models.values.InternalId
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import v2.models.AccessCode
import v2.models.Balance
import v2.models.BalanceRequest
import v2.models.CurrencyCL
import v2.models.GuaranteeReferenceNumber
import v2.models.InternalBalanceResponse

trait Generators {

  implicit val accessCodeGenerator: Arbitrary[AccessCode] = Arbitrary {
    Gen.stringOfN(4, Gen.alphaNumChar).map(AccessCode(_))
  }

  implicit val balanceRequestGenerator: Arbitrary[BalanceRequest] = Arbitrary {
    arbitrary[AccessCode].map(BalanceRequest(_))
  }

  // [0-9]{2}[A-Z]{2}[A-Z0-9]{12}[0-9]([A-Z][0-9]{6})?
  implicit val guaranteeReferenceNumberGenerator: Arbitrary[GuaranteeReferenceNumber] = Arbitrary {
    for {
      year     <- Gen.choose(23, 39).map(_.toString)
      country  <- Gen.oneOf("GB", "XI")
      alphanum <- Gen.stringOfN(12, Gen.alphaNumChar).map(_.toUpperCase)
      num1     <- Gen.numChar.map(_.toString)
      alpha    <- Gen.alphaChar.map(_.toString.toUpperCase)
      num      <- Gen.stringOfN(6, Gen.numChar)
    } yield GuaranteeReferenceNumber(s"$year$country$alphanum$num1$alpha$num")
  }

  implicit val amountGenerator: Arbitrary[Balance] = Arbitrary {
    Gen.chooseNum(0.0, Double.MaxValue).map(Balance(_))
  }

  implicit val internalBalanceResponseGenerator: Arbitrary[InternalBalanceResponse] = Arbitrary {
    for {
      grn     <- guaranteeReferenceNumberGenerator.arbitrary
      balance <- arbitrary[Balance]
    } yield InternalBalanceResponse(grn, balance, CurrencyCL("GBP"))
  }

  implicit val internalIdGenerator: Arbitrary[InternalId] = Arbitrary {
    Gen.stringOfN(18, Gen.alphaNumChar).map(InternalId(_))
  }

}
