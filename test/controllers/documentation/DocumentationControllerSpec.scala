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

package controllers.documentation

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.testkit.TestKit
import controllers.Assets
import controllers.AssetsConfiguration
import controllers.DefaultAssetsMetadata
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.Environment
import play.api.Mode
import play.api.http.DefaultFileMimeTypes
import play.api.http.DefaultHttpErrorHandler
import play.api.http.HttpConfiguration
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers._

class DocumentationControllerSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val system                = ActorSystem.create(suiteName)
  implicit val materializer = Materializer(system)

  val environment   = Environment.simple(mode = Mode.Test)
  val configuration = Configuration.load(environment)

  val assets = {
    val assetsConfig     = AssetsConfiguration.fromConfiguration(configuration, environment.mode)
    val httpConfig       = HttpConfiguration.fromConfiguration(configuration, environment)
    val fileMimeTypes    = new DefaultFileMimeTypes(httpConfig.fileMimeTypes)
    val httpErrorHandler = new DefaultHttpErrorHandler(environment, configuration, None, None)
    val assetsMetadata   = new DefaultAssetsMetadata(environment, assetsConfig, fileMimeTypes)
    new Assets(httpErrorHandler, assetsMetadata)
  }

  val controller = new DocumentationController(assets, Helpers.stubControllerComponents())

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "DocumentationController" should "return 200 and JSON response at the definition route" in {
    val result = controller.definition()(FakeRequest())
    status(result) shouldBe OK
    contentType(result) should contain(JSON)
    contentAsJson(result)
  }

  // Not DocumentationController, but demonstrates that the approach used in definition.routes works
  "Public assets route" should "return 200 and plain text response for the RAML definition file" in {
    val result = assets.at("/public/api/conf", "1.0/application.raml")(FakeRequest())
    status(result) shouldBe OK
    contentType(result) should contain(BINARY)
  }

  it should "return 200 and plain text response for the overview Markdown file" in {
    val result = assets.at("/public/api/conf", "1.0/docs/overview.md")(FakeRequest())
    status(result) shouldBe OK
    contentType(result) should contain(BINARY)
  }
}
