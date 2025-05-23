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

package controllers.documentation

import org.apache.pekko.stream.Materializer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._

class DocumentationRoutesSpec extends AnyFlatSpec with Matchers with GuiceOneAppPerSuite {

  implicit val materializer: Materializer = app.injector.instanceOf[Materializer]

  "API documentation route" should "return 200 and JSON response for definition.json" in {
    val request = FakeRequest(Call("GET", "/api/definition"))
    val result  = route(app, request).get

    status(result) shouldBe OK
    contentType(result) should contain(JSON)

    val definitionJson = contentAsJson(result)
    val apiContext     = (definitionJson \ "api" \ "context").as[String]

    apiContext shouldBe "customs/guarantees"
  }

  it should "return 200 and plain text response for the OAS YAML definition file" in {
    val request = FakeRequest(Call("GET", "/api/conf/2.0/application.yaml"))
    val result  = route(app, request).get
    status(result) shouldBe OK
    contentType(result) should contain(BINARY)
  }

  it should "return 200 and plain text response for the overview Markdown file" in {
    val request = FakeRequest(Call("GET", "/api/conf/common/overview.md"))
    val result  = route(app, request).get
    status(result) shouldBe OK
    contentType(result) should contain(BINARY)
  }

  it should "return 404 for nonexistent resource" in {
    val request = FakeRequest(Call("GET", "/api/conf/2.0/docs/foobar.md"))
    val result  = route(app, request).get
    status(result) shouldBe NOT_FOUND
  }
}
