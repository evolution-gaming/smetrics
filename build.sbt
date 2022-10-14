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
  homepage := Some(new URL("http://github.com/evolution-gaming/smetrics")),
  startYear := Some(2019),
  organizationName := "Evolution",
  organizationHomepage := Some(url("http://evolution.com")),
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq("2.13.10", "3.2.0", "2.12.16"),
  publishTo := Some(Resolver.evolutionReleases),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  releaseCrossBuild := true,
  Compile / doc / scalacOptions += "-no-link-warnings")


lazy val root = (project
  in file(".")
  settings commonSettings
  settings (
    publish / skip := true,
    name := "smetrics-parent"
  )
  aggregate(smetrics, prometheus, http4s, doobie, kafka))

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
      if2 = List(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full))
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

lazy val kafka = (project
  in file("modules/kafka")
  settings commonSettings
  dependsOn prometheus % "compile->compile;test->test"
  settings(
    name := "smetrics-prometheus-kafka",
    publish / skip := is3(scalaVersion.value),
    libraryDependencies ++= crossSettings(
      scalaVersion.value,
      if3 = Nil,
      if2 = List(Dependencies.skafka),
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
    libraryDependencies += Dependencies.doobie
  )
)
