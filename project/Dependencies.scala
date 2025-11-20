import sbt.*

object Dependencies {

  private val prometheusVersion = "0.9.0"
  val prometheus                = "io.prometheus"        % "simpleclient"        % prometheusVersion
  val prometheusCommon          = "io.prometheus"        % "simpleclient_common" % prometheusVersion
  val scalatest                 = "org.scalatest"       %% "scalatest"           % "3.2.19"
  val `cats-helper`             = "com.evolutiongaming" %% "cats-helper"         % "3.11.0"
  val http4s                    = "org.http4s"          %% "http4s-core"         % "0.23.30"
  val doobie                    = "org.tpolecat"        %% "doobie-core"         % "1.0.0-RC8"

  object Cats {
    val core   = "org.typelevel" %% "cats-core"   % "2.13.0"
    val effect = "org.typelevel" %% "cats-effect" % "3.5.7"
  }

  object PrometheusV1 {
    private val version = "1.3.8"
    val core            = "io.prometheus" % "prometheus-metrics-core"                   % version
    val formats         = "io.prometheus" % "prometheus-metrics-exposition-textformats" % version
  }

  object Logback {
    private val version = "1.5.18"
    val classic         = "ch.qos.logback" % "logback-classic" % version
  }
}
