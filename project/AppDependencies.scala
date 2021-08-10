import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {
  val bootstrapVersion = "5.10.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-28" % bootstrapVersion
  )

  val test = Seq(
    "uk.gov.hmrc"         %% "bootstrap-test-play-28" % bootstrapVersion,
    "com.typesafe.akka"   %% "akka-testkit"           % PlayVersion.akkaVersion,
    "com.vladsch.flexmark" % "flexmark-all"           % "0.36.8"
  ).map(_ % s"$Test, $IntegrationTest")
}
