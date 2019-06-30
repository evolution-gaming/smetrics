package com.evolutiongaming.smetrics

object MetricsHelper {

  implicit class SmetricsMetricsLongOps(val self: Long) extends AnyVal {

    def millisToSeconds: Long = self / 1000

    def nanosToSeconds: Long = self / 1000000
  }
}
