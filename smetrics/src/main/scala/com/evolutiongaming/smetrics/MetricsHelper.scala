package com.evolutiongaming.smetrics

object MetricsHelper {

  implicit class SmetricsMetricsLongOps(val self: Long) extends AnyVal {

    def millisToSeconds: Double = self.toDouble / 1000

    def nanosToSeconds: Double = self.toDouble / 1000000000

    def nanosToMillis: Double = self.toDouble / 1000
  }
}
