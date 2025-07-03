import play.sbt.routes.RoutesKeys
import sbt.Keys.scalacOptions
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.6.3"

val appName = "common-transit-convention-guarantee-balance"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(
    JUnitXmlReportPlugin
  ) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(inThisBuild(buildSettings))
  .settings(scalacSettings)
  .settings(scoverageSettings)
  .settings(
    PlayKeys.playDefaultPort := 10207,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    RoutesKeys.routesImport ++= Seq(
      "models.values._",
      "v2.models.Binders._",
      "v2.models.GuaranteeReferenceNumber"
    )
  )

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(scalacSettings)
  .settings(
    libraryDependencies ++= AppDependencies.test
  )

lazy val buildSettings = Def.settings(
  scalafmtOnCompile := true,
  scalafixDependencies ++= Seq(
    "com.github.liancheng" %% "organize-imports" % "0.6.0"
  )
)

lazy val scalacSettings = Def.settings(
  scalacOptions ++= Seq(
    "-Wconf:src=routes/.*:s",
    "-Wconf:msg=Flag.*repeatedly:s"
  ),
  Test / scalacOptions ~= {
    opts =>
      opts.filterNot(Set("-Wdead-code"))
  },
  // Disable warnings arising from generated routing code
  scalacOptions += "-Wconf:src=routes/.*:silent",
  // Disable fatal warnings and warnings from discarding values
  scalacOptions ~= {
    opts =>
      opts.filterNot(Set("-Xfatal-warnings", "-Ywarn-value-discard"))
  },
  scalacOptions := scalacOptions.value.map {
    case "-Ykind-projector" => "-Xkind-projector"
    case option             => option
  }
)

lazy val scoverageSettings = Def.settings(
  Test / parallelExecution := false,
  ScoverageKeys.coverageMinimumStmtTotal := 90,
  ScoverageKeys.coverageFailOnMinimum := true,
  ScoverageKeys.coverageHighlighting := true,
  ScoverageKeys.coverageExcludedPackages := Seq(
    "<empty>",
    "Reverse.*",
    ".*(config|views.*)",
    ".*(BuildInfo|Routes).*"
  ).mkString(";"),
  ScoverageKeys.coverageExcludedFiles := Seq(
    "<empty>",
    "Reverse.*",
    ".*BuildInfo.*",
    ".*javascript.*",
    ".*Routes.*",
    ".*GuiceInjector"
  ).mkString(";")
)
