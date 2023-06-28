import sbt.librarymanagement.For3Use2_13
import Dependencies._

def crossSettings[T](scalaVersion: String, if3: List[T] = Nil, if2: List[T] = Nil) = scalaVersion match {
  case version if version.startsWith("3") => if3
  case _                                  => if2
}

lazy val commonSettings = Seq(
  homepage := Some(new URL("http://github.com/evolution-gaming/cats-helper")),
  organization := "com.evolution",
  organizationName := "Evolution",
  organizationHomepage := Some(url("http://evolution.com")),

  startYear := Some(2019),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),

  versionScheme := Some("semver-spec"),
  crossScalaVersions := Seq("2.13.11", "3.3.0", "2.12.18"),
  scalaVersion := crossScalaVersions.value.head,

  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",

  scmInfo := Some(
    ScmInfo(
      url("https://github.com/evolution-gaming/smetrics"),
      "git@github.com:evolution-gaming/smetrics.git"
    )
  ),
  Test / publishArtifact := false,
  Compile / doc / scalacOptions += "-no-link-warnings"
)

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    name := "smetrics-parent"
  )
  .aggregate(smetrics, prometheus, http4s, doobie)

lazy val smetrics = project
  .in(file("smetrics"))
  .settings(commonSettings)
  .settings(
    name := "smetrics",
    libraryDependencies ++= Seq(Cats.core, Cats.effect, `cats-helper` % Test, scalatest % Test),
    libraryDependencies ++= crossSettings(
      scalaVersion.value,
      if2 = List(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full))
    ),
    scalacOptions ++= crossSettings(
      scalaVersion.value,
      if3 = List("-Ykind-projector:underscores", "-language:implicitConversions"),
      if2 = List("-Xsource:3", "-P:kind-projector:underscore-placeholders")
    )
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
      scalatest % Test
    )
  )

lazy val http4s = project
  .in(file("modules/http4s"))
  .settings(commonSettings)
  .dependsOn(smetrics % "compile->compile;test->test")
  .settings(
    name := "smetrics-http4s",
    libraryDependencies += Dependencies.http4s
  )

lazy val doobie = project
  .in(file("modules/doobie"))
  .settings(commonSettings)
  .dependsOn(smetrics % "compile->compile;test->test")
  .settings(
    name := "smetrics-doobie",
    libraryDependencies += Dependencies.doobie,
    libraryDependencies ++= Seq(Dependencies.doobie, `cats-helper`)
  )
