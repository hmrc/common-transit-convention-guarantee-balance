import sbt._

object AppDependencies {
  val catsVersion       = "2.13.0"
  val log4catsVersion   = "2.7.0"
  val bootstrapVersion  = "9.13.0"
  val hmrcMongoVersion  = "2.6.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30"   % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"          % hmrcMongoVersion,
    "io.lemonlabs"      %% "scala-uri"                   % "4.0.3",
    "org.typelevel"     %% "cats-core"                   % catsVersion,
    "org.typelevel"     %% "log4cats-slf4j"              % log4catsVersion,
    "org.apache.pekko"  %% "pekko-connectors-xml"        % "1.1.0",
    "org.apache.pekko"  %% "pekko-slf4j"                 % "1.1.3",
    "org.apache.pekko"  %% "pekko-serialization-jackson" % "1.1.3",
    "org.apache.pekko"  %% "pekko-actor-typed"           % "1.1.3"
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"       %% "scalatest"               % "3.2.19",
    "org.scalatestplus"   %% "scalacheck-1-18"         % "3.2.19.0",
    "uk.gov.hmrc"         %% "bootstrap-test-play-30"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"   %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "org.apache.pekko"    %% "pekko-testkit"           % "1.1.3",
    "org.mockito"          % "mockito-core"            % "5.17.0",
    "org.scalatestplus"   %% "mockito-5-12"            % "3.2.19.0",
    "com.vladsch.flexmark" % "flexmark-all"            % "0.64.8"
  ).map(_ % Test)
}
