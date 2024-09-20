import sbt._

object AppDependencies {
  val catsVersion       = "2.9.0"
  val catsEffectVersion = "3.4.4"
  val log4catsVersion   = "2.5.0"
  val bootstrapVersion  = "9.3.0"
  val hmrcMongoVersion  = "1.4.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "play-json-union-formatter" % "1.17.0-play-28",
    "io.lemonlabs"      %% "scala-uri"                 % "3.6.0",
    "org.typelevel"     %% "cats-core"                 % catsVersion,
    "org.typelevel"     %% "cats-effect"               % catsEffectVersion,
    "org.typelevel"     %% "log4cats-slf4j"            % log4catsVersion,
    "org.apache.pekko"  %% "pekko-connectors-xml"      % "1.0.1",
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"       %% "scalatest"               % "3.2.10",
    "org.scalatestplus"   %% "scalacheck-1-15"         % "3.2.10.0",
    "uk.gov.hmrc"         %% "bootstrap-test-play-30"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"   %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "org.apache.pekko"    %% "pekko-testkit"           % "1.0.3",
    "org.mockito"         %% "mockito-scala-scalatest" % "1.17.29",
    "com.vladsch.flexmark" % "flexmark-all"            % "0.62.2"
  ).map(_ % Test)
}
