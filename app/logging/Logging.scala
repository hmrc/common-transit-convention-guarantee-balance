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

package logging

import cats.effect.IO
import org.slf4j.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.gov.hmrc.http.HeaderCarrier

trait Logging {
  val slf4jLogger         = LoggerFactory.getLogger(getClass())
  private val basicLogger = Slf4jLogger.getLoggerFromSlf4j[IO](slf4jLogger)

  def logger(implicit hc: HeaderCarrier) = basicLogger.addContext(hc.mdcData)
}
