import sbt._

object Dependencies {

  private val prometheusVersion = "0.9.0"
  val prometheus       = "io.prometheus"        % "simpleclient"        % prometheusVersion
  val prometheusCommon = "io.prometheus"        % "simpleclient_common" % prometheusVersion
  val scalatest        = "org.scalatest"       %% "scalatest"           % "3.2.9"
  // TODO replace with real version after migration https://github.com/evolution-gaming/cats-helper/pull/103
  val `cats-helper`    = "com.evolutiongaming" %% "cats-helper"         % "2.6.3-SNAPSHOT"
  val http4s           = "org.http4s"          %% "http4s-core"         % "0.23.7"
  val doobie           = "org.tpolecat"        %% "doobie-core"         % "1.0.0-RC1"

  object Cats {
    private val version = "2.7.0"
    private val `effects-version` = "3.3.4"
    val core   = "org.typelevel" %% "cats-core"   % version
    val effect = "org.typelevel" %% "cats-effect" % `effects-version`
  }
}
