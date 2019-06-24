package com.evolutiongaming.smetrics

object MetricsHelper {

  implicit class MetricsLongOps(val self: Long) extends AnyVal {
    def toSeconds: Double = self.toDouble / 1000
  }
}
