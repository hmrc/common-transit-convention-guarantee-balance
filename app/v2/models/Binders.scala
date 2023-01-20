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

import play.api.mvc.PathBindable

object Binders {

  implicit val grnBinder: PathBindable[GuaranteeReferenceNumber] = new PathBindable[GuaranteeReferenceNumber] {
    private val grnPattern = """[0-9]{2}([A-Z]{2})[A-Z0-9]{12}[0-9]([A-Z][0-9]{6})?""".r

    override def bind(key: String, value: String): Either[String, GuaranteeReferenceNumber] = value match {
      case grnPattern("GB", _) | grnPattern("XI", _) => Right(GuaranteeReferenceNumber(value))
      case grnPattern(_, _)                          => Left("ERROR_INVALID_GRN_COUNTRY_CODE")
      case _                                         => Left("ERROR_INVALID_GRN_FORMAT")
    }

    override def unbind(key: String, value: GuaranteeReferenceNumber): String = value.value
  }

}
