import sbt.librarymanagement.For3Use2_13
import Dependencies._

def crossSettings[T](scalaVersion: String, if3: Seq[T], if2: Seq[T]) = {
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((3, _)) => if3
    case Some((2, 12 | 13)) => if2
    case _ => Nil
  }
}

def is3(scalaVersion: String): Boolean = scalaVersion.startsWith("3")

lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(url("https://github.com/evolution-gaming/smetrics")),
  startYear := Some(2019),
  organizationName := "Evolution",
  organizationHomepage := Some(url("https://evolution.com")),
  versionPolicyIntention := Compatibility.BinaryCompatible,
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq("2.13.17", "3.3.5"),
  publishTo := Some(Resolver.evolutionReleases),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  Compile / doc / scalacOptions += "-no-link-warnings")

val alias: Seq[sbt.Def.Setting[?]] =
  addCommandAlias("check", "all versionPolicyCheck Compile/doc") ++
    addCommandAlias("build", "+all compile test")

lazy val root = (project
  in file(".")
  settings commonSettings
  settings (alias)
  settings (
    publish / skip := true,
    name := "smetrics-parent"
  )
  aggregate(smetrics, prometheus, http4s, doobie, prometheus_v1))

lazy val smetrics = (project
  in file("smetrics")
  settings commonSettings
  settings(
    name := "smetrics",
    scalacOptsFailOnWarn := Some(false),
    libraryDependencies ++= Seq(
      Cats.core,
      Cats.effect,
      `cats-helper`,
      scalatest % Test),
    libraryDependencies ++= crossSettings(
      scalaVersion.value,
      if3 = Nil,
      if2 = List(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full))
    ),
    scalacOptions ++= crossSettings(
      scalaVersion.value,
      if3 = Seq("-Ykind-projector:underscores", "-language:implicitConversions"),
      if2 = Seq("-Xsource:3", "-P:kind-projector:underscore-placeholders")
    )
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
      scalatest % Test
    )
  )
)

lazy val prometheus_v1 = (project
  in file("modules/prometheus_v1")
  settings commonSettings
  dependsOn smetrics % "compile->compile;test->test"
  settings (
    name := "smetrics-prometheus-v1",
    libraryDependencies ++= Seq(
      Dependencies.PrometheusV1.core,
      Dependencies.PrometheusV1.formats,
      scalatest % Test
    ),
  )
)

lazy val http4s = (project
  in file("modules/http4s")
  settings commonSettings
  dependsOn smetrics % "compile->compile;test->test"
  settings(
    name := "smetrics-http4s",
    libraryDependencies += Dependencies.http4s
  )
)

lazy val doobie = (project
  in file("modules/doobie")
  settings commonSettings
  dependsOn smetrics % "compile->compile;test->test"
  settings(
    name := "smetrics-doobie",
    libraryDependencies += Dependencies.doobie,
  libraryDependencies ++= Seq(
    Dependencies.doobie,
    `cats-helper`)
  )
)
