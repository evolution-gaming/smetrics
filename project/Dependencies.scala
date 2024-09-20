import sbt.*

object Dependencies {

  private val prometheusVersion = "0.9.0"
  val prometheus       = "io.prometheus"        % "simpleclient"        % prometheusVersion
  val prometheusCommon = "io.prometheus"        % "simpleclient_common" % prometheusVersion
  val scalatest        = "org.scalatest"       %% "scalatest"           % "3.2.15"
  val `cats-helper`    = "com.evolutiongaming" %% "cats-helper"         % "3.7.0"
  val http4s           = "org.http4s"          %% "http4s-core"         % "0.23.28"
  val doobie           = "org.tpolecat"        %% "doobie-core"         % "1.0.0-RC6"

  object Cats {
    val core   = "org.typelevel" %% "cats-core"   % "2.9.0"
    val effect = "org.typelevel" %% "cats-effect" % "3.4.8"
  }
}
