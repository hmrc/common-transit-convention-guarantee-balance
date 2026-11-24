import sbt._

object AppDependencies {
  val catsVersion       = "2.13.0"
  val log4catsVersion   = "2.8.0"
  val bootstrapVersion  = "10.9.0"
  val hmrcMongoVersion  = "2.14.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30"   % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"          % hmrcMongoVersion,
    "io.lemonlabs"      %% "scala-uri"                   % "4.0.3",
    "org.typelevel"     %% "cats-core"                   % catsVersion,
    "org.typelevel"     %% "log4cats-slf4j"              % log4catsVersion,
    "org.apache.pekko"  %% "pekko-connectors-xml"        % "1.3.0",
    "org.apache.pekko"  %% "pekko-slf4j"                 % "2.0.0",
    "org.apache.pekko"  %% "pekko-serialization-jackson" % "2.0.0",
    "org.apache.pekko"  %% "pekko-actor-typed"           % "2.0.0",
    "org.apache.pekko"  %% "pekko-protobuf-v3"           % "2.0.0",
    "org.apache.pekko"  %% "pekko-stream"                % "2.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"       %% "scalatest"               % "3.2.20",
    "org.scalatestplus"   %% "scalacheck-1-18"         % "3.2.19.0",
    "uk.gov.hmrc"         %% "bootstrap-test-play-30"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"   %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "org.apache.pekko"    %% "pekko-testkit"           % "2.0.0",
    "org.mockito"          % "mockito-core"            % "5.23.0",
    "org.scalatestplus"   %% "mockito-5-12"            % "3.2.19.0",
    "com.vladsch.flexmark" % "flexmark-all"            % "0.64.8"
  ).map(_ % Test)
}
