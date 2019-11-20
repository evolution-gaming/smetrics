import Dependencies._

lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(new URL("http://github.com/evolution-gaming/smetrics")),
  startYear := Some(2019),
  organizationName := "Evolution Gaming",
  organizationHomepage := Some(url("http://evolutiongaming.com")),
  bintrayOrganization := Some("evolutiongaming"),
  scalaVersion := crossScalaVersions.value.last,
  crossScalaVersions := Seq("2.12.10", "2.13.1"),
  resolvers += Resolver.bintrayRepo("evolutiongaming", "maven"),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  releaseCrossBuild := true,
  Compile / doc / scalacOptions += "-no-link-warnings")


lazy val root = (project
  in file(".")
  settings commonSettings
  settings (
    publish / skip := true,
    name := "smetrics")
  aggregate(smetrics, prometheus))

lazy val smetrics = (project
  in file("smetrics")
  settings commonSettings
  settings(
    name := "smetrics",
    libraryDependencies ++= Seq(
      Cats.core,
      Cats.effect,
      `cats-helper`,
      scalatest % Test)))

lazy val prometheus = (project
  in file("modules/prometheus")
  settings commonSettings
  dependsOn smetrics % "compile->compile;test->test"
  settings (
    name := "smetrics-prometheus",
    libraryDependencies ++= Seq(
      Dependencies.prometheus,
      scalatest % Test)))