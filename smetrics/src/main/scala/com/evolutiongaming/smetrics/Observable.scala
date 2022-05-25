package com.evolutiongaming.smetrics

/**
 * Common trait for Summary and Histogram.
 */
trait Observable[F[_]] {
  def observe(value: Double): F[Unit]
}
