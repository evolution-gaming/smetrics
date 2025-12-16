import Dependencies.*
import com.typesafe.tools.mima.core.*

lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(url("https://github.com/evolution-gaming/smetrics")),
  startYear := Some(2019),
  organizationName := "Evolution",
  organizationHomepage := Some(url("https://evolution.com")),
  versionPolicyIntention := Compatibility.BinaryCompatible,
  crossScalaVersions := Seq("2.13.18", "3.3.7"),
  scalaVersion := crossScalaVersions.value.head,
  scalacOptions ++= Seq(
    "-release:17",
    "-deprecation",
  ),
  scalacOptions ++= crossSettings(
    scalaVersion.value,
    if3 = Seq(
      "-Ykind-projector:underscores",

      // disable new brace-less syntax:
      // https://alexn.org/blog/2022/10/24/scala-3-optional-braces/
      "-no-indent",

      // improve error messages:
      "-explain",
      "-explain-types",
    ),
    if2 = Seq(
      "-Xsource:3",
      "-P:kind-projector:underscore-placeholders",
    ),
  ),
  libraryDependencies ++= crossSettings(
    scalaVersion.value,
    if3 = Nil,
    if2 = List(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.4" cross CrossVersion.full)),
  ),
  publishTo := Some(Resolver.evolutionReleases),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  Compile / doc / scalacOptions += "-no-link-warnings",
)

ThisBuild / mimaBinaryIssueFilters ++= Seq(
  // add mima check exceptions here, like:
//  ProblemFilters.exclude[ReversedMissingMethodProblem]("com.evolutiongaming.smetrics.CollectorRegistry.info"),
)

ThisBuild / libraryDependencySchemes ++= Seq(
  // add mima check overrides for RC-level libraries, like:
//  "org.tpolecat" %% "doobie-core" % VersionScheme.Always,
)

val alias: Seq[sbt.Def.Setting[?]] =
  addCommandAlias("check", "all versionPolicyCheck Compile/doc") ++
    addCommandAlias("build", "+all compile test") ++
    addCommandAlias("fmt", "+all scalafmtAll scalafmtSbt")

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(alias)
  .settings(
    publish / skip := true,
    name := "smetrics-parent",
  )
  .aggregate(smetrics, prometheus, http4s, doobie, prometheus_v1, logback, sttp3, sttp4)

lazy val smetrics = project
  .in(file("smetrics"))
  .settings(commonSettings)
  .settings(
    name := "smetrics",
    scalacOptsFailOnWarn := Some(false),
    libraryDependencies ++= Seq(
      Cats.core,
      Cats.effect,
      `cats-helper`,
      scalatest % Test,
    ),
  )

lazy val prometheus = project
  .in(file("modules/prometheus"))
  .settings(commonSettings)
  .dependsOn(smetrics % "compile->compile;test->test")
  .settings(
    name := "smetrics-prometheus",
    libraryDependencies ++= Seq(
      Dependencies.prometheus,
      Dependencies.prometheusCommon,
      scalatest % Test,
    ),
  )

lazy val prometheus_v1 = project
  .in(file("modules/prometheus_v1"))
  .settings(commonSettings)
  .dependsOn(smetrics % "compile->compile;test->test")
  .settings(
    name := "smetrics-prometheus-v1",
    libraryDependencies ++= Seq(
      Dependencies.PrometheusV1.core,
      Dependencies.PrometheusV1.formats,
      scalatest % Test,
    ),
  )

lazy val logback = project
  .in(file("modules/logback"))
  .settings(commonSettings)
  .dependsOn(smetrics % "compile->compile;test->test")
  .settings(
    name := "smetrics-logback",
    libraryDependencies ++= Seq(
      Dependencies.PrometheusV1.core,
      Dependencies.Logback.classic,
    ),
    versionPolicyCheck / skip := true,
  )

lazy val http4s = project
  .in(file("modules/http4s"))
  .settings(commonSettings)
  .dependsOn(smetrics % "compile->compile;test->test")
  .settings(
    name := "smetrics-http4s",
    libraryDependencies += Dependencies.http4s,
  )

lazy val doobie = project
  .in(file("modules/doobie"))
  .settings(commonSettings)
  .dependsOn(smetrics % "compile->compile;test->test")
  .settings(
    name := "smetrics-doobie",
    libraryDependencies += Dependencies.doobie,
    libraryDependencies ++= Seq(
      Dependencies.doobie,
      `cats-helper`,
    ),
  )

lazy val sttp3 = project
  .in(file("modules/sttp3"))
  .settings(commonSettings)
  .dependsOn(smetrics % "compile->compile;test->test")
  .settings(
    name := "smetrics-sttp3",
    libraryDependencies ++= Seq(
      Dependencies.Sttp3.core,
      Dependencies.Sttp3.catsBackend % Test,
    ),
  )

lazy val sttp4 = project
  .in(file("modules/sttp4"))
  .settings(commonSettings)
  .dependsOn(smetrics % "compile->compile;test->test")
  .settings(
    name := "smetrics-sttp4",
    libraryDependencies ++= Seq(
      Dependencies.Sttp4.core,
      Dependencies.Sttp4.catsBackend % Test,
    ),
    versionPolicyCheck / skip := true,
  )

def crossSettings[T](scalaVersion: String, if3: Seq[T], if2: Seq[T]): Seq[T] = {
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((3, _)) => if3
    case Some((2, 12 | 13)) => if2
    case _ => Nil
  }
}
