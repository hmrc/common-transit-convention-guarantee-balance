import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {
  val catsVersion       = "2.6.1"
  val catsEffectVersion = "3.2.9"
  val log4catsVersion   = "2.1.1"
  val bootstrapVersion  = "5.14.0"

  val compile = Seq(
    "uk.gov.hmrc"   %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc"   %% "play-json-union-formatter" % "1.15.0-play-28",
    "io.lemonlabs"  %% "scala-uri"                 % "3.6.0",
    "org.typelevel" %% "cats-core"                 % catsVersion,
    "org.typelevel" %% "cats-effect"               % catsEffectVersion,
    "org.typelevel" %% "log4cats-slf4j"            % log4catsVersion,
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )

  val test = Seq(
    "org.scalatest"       %% "scalatest"              % "3.2.10",
    "org.scalatestplus"   %% "scalacheck-1-15"        % "3.2.10.0",
    "uk.gov.hmrc"         %% "bootstrap-test-play-28" % bootstrapVersion,
    "com.typesafe.akka"   %% "akka-testkit"           % PlayVersion.akkaVersion,
    "org.mockito"         %% "mockito-scala"          % "1.16.42",
    "com.vladsch.flexmark" % "flexmark-all"           % "0.62.2"
  ).map(_ % s"$Test, $IntegrationTest")
}
