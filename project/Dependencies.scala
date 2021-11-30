import sbt._

object Dependencies {

  private val prometheusVersion = "0.12.0"
  val prometheus       = "io.prometheus"        % "simpleclient"        % prometheusVersion
  val prometheusCommon = "io.prometheus"        % "simpleclient_common" % prometheusVersion
  val scalatest        = "org.scalatest"       %% "scalatest"           % "3.2.9"
  val `cats-helper`    = "com.evolutiongaming" %% "cats-helper"         % "2.6.2"
  val http4s           = "org.http4s"          %% "http4s-core"         % "0.22.7"
  val doobie           = "org.tpolecat"        %% "doobie-core"         % "0.9.0"

  object Cats {
    private val version = "2.3.0"
    val core   = "org.typelevel" %% "cats-core"   % version
    val effect = "org.typelevel" %% "cats-effect" % "2.4.1"
  }
}
