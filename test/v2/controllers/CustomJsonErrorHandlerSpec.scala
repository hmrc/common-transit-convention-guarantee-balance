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

package v2.controllers

import com.typesafe.config.ConfigFactory
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CustomJsonErrorHandlerSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  private class Setup(
    config: Map[String, Any] = Map("appName" -> "common-transit-convention-guarantee-balance")
  ) {

    val request = FakeRequest(
      "POST",
      "/",
      FakeHeaders(
        Seq(
          HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json"
        )
      ),
      Json.obj(
        "accessCode" -> "1"
      )
    )

    val auditConnector = mock[AuditConnector]
    when(auditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(Success))

    val httpAuditEvent = mock[HttpAuditEvent](withSettings.lenient)

    val configuration =
      Configuration.from(config).withFallback(Configuration(ConfigFactory.load()))

    lazy val jsonErrorHandler = new CustomJsonErrorHandler(auditConnector, httpAuditEvent, configuration)
  }

  "onClientError" should {

    "return 400 wth json response for invalid GRN country code" in new Setup {

      val result = jsonErrorHandler.onClientError(request, BAD_REQUEST, "ERROR_INVALID_GRN_COUNTRY_CODE")

      status(result) shouldEqual BAD_REQUEST
      contentAsJson(result) shouldEqual Json
        .obj("code" -> "BAD_REQUEST", "message" -> "The guarantee reference number must be for a GB or XI guarantee.")
    }

    "return 400 wth json response for invalid GRN format" in new Setup {

      val result = jsonErrorHandler.onClientError(request, BAD_REQUEST, "ERROR_INVALID_GRN_FORMAT")

      status(result) shouldEqual BAD_REQUEST
      contentAsJson(result) shouldEqual Json
        .obj("code" -> "BAD_REQUEST", "message" -> "The guarantee reference number is not in the correct format.")
    }

    "return 400 wth json response for unknown message" in new Setup {

      val result = jsonErrorHandler.onClientError(request, BAD_REQUEST, "Unknown bad request error")

      status(result) shouldEqual BAD_REQUEST
      contentAsJson(result) shouldEqual Json
        .obj("statusCode" -> BAD_REQUEST, "message" -> "bad request, cause: REDACTED")
    }

  }

}
