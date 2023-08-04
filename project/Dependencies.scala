import sbt._

object Dependencies {

  private val prometheusVersion = "0.9.0"
  val prometheus       = "io.prometheus"        % "simpleclient"        % prometheusVersion
  val prometheusCommon = "io.prometheus"        % "simpleclient_common" % prometheusVersion
  val scalatest        = "org.scalatest"       %% "scalatest"           % "3.2.15"
  val `cats-helper`    = "com.evolutiongaming" %% "cats-helper"         % "2.13.0"
  val http4s           = "org.http4s"          %% "http4s-core"         % "0.22.7"
  val doobie           = "org.tpolecat"        %% "doobie-core"         % "0.9.4"

  object Cats {
    val core   = "org.typelevel" %% "cats-core"   % "2.6.1"
    val effect = "org.typelevel" %% "cats-effect" % "2.5.5"
  }
}
