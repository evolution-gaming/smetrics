import Dependencies._

lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(new URL("http://github.com/evolution-gaming/smetrics")),
  startYear := Some(2019),
  organizationName := "Evolution",
  organizationHomepage := Some(url("http://evolution.com")),
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq("2.13.5", "2.12.15"),
  publishTo := Some(Resolver.evolutionReleases),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  releaseCrossBuild := true,
  Compile / doc / scalacOptions += "-no-link-warnings")


lazy val root = (project
  in file(".")
  settings commonSettings
  settings (
    publish / skip := true,
    name := "smetrics")
  aggregate(smetrics, prometheus, http4s, doobie))

lazy val smetrics = (project
  in file("smetrics")
  settings commonSettings
  settings(
    name := "smetrics",
    libraryDependencies ++= Seq(
      Cats.core,
      Cats.effect,
      `cats-helper`,
      scalatest % Test),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.0" cross CrossVersion.full)
))

lazy val prometheus = (project
  in file("modules/prometheus")
  settings commonSettings
  dependsOn smetrics % "compile->compile;test->test"
  settings (
    name := "smetrics-prometheus",
    libraryDependencies ++= Seq(
      Dependencies.prometheus,
      Dependencies.prometheusCommon,
      scalatest % Test)))

lazy val http4s = (project
  in file("modules/http4s")
  settings commonSettings
  dependsOn smetrics % "compile->compile;test->test"
  settings(
  name := "smetrics-http4s",
  libraryDependencies += Dependencies.http4s))

lazy val doobie = (project
  in file("modules/doobie")
  settings commonSettings
  dependsOn smetrics % "compile->compile;test->test"
  settings(
  name := "smetrics-doobie",
  libraryDependencies += Dependencies.doobie))