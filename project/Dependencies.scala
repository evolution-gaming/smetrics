import sbt._

object Dependencies {

  private val prometheusVersion = "0.9.0"
  val prometheus       = "io.prometheus"        % "simpleclient"        % prometheusVersion
  val prometheusCommon = "io.prometheus"        % "simpleclient_common" % prometheusVersion
  val scalatest        = "org.scalatest"       %% "scalatest"           % "3.2.9"
  val `cats-helper`    = "com.evolutiongaming" %% "cats-helper"         % "2.7.6"
  val http4s           = "org.http4s"          %% "http4s-core"         % "0.22.7"
  val doobie           = "org.tpolecat"        %% "doobie-core"         % "0.9.0"
  val skafka           = "com.evolutiongaming" %% "skafka"              % "11.13.2"

  object Cats {
    private val version = "2.6.1"
    val core   = "org.typelevel" %% "cats-core"   % version
    val effect = "org.typelevel" %% "cats-effect" % "2.5.4"
  }
}
