import sbt._

object Dependencies {

  private val prometheusVersion = "0.9.0"
  val prometheus       = "io.prometheus"        % "simpleclient"        % prometheusVersion
  val prometheusCommon = "io.prometheus"        % "simpleclient_common" % prometheusVersion
  val scalatest        = "org.scalatest"       %% "scalatest"           % "3.2.10"
  val `cats-helper`    = "com.evolutiongaming" %% "cats-helper"         % "3.0.1"
  val http4s           = "org.http4s"          %% "http4s-core"         % "0.23.7"
  val doobie           = "org.tpolecat"        %% "doobie-core"         % "1.0.0-RC2"

  object Cats {
    private val version = "2.7.0"
    private val `effects-version` = "3.3.4"
    val core   = "org.typelevel" %% "cats-core"   % version
    val effect = "org.typelevel" %% "cats-effect" % `effects-version`
  }
}
