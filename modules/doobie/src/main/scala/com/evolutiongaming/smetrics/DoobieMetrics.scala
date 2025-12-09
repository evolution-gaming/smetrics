package com.evolutiongaming.smetrics

import cats.Monad
import cats.effect.Resource
import cats.implicits.*
import com.evolutiongaming.smetrics.MetricsHelper.*

import scala.concurrent.duration.FiniteDuration

trait DoobieMetrics[F[_]] {
  def query(time: FiniteDuration, success: Boolean): F[Unit]
}

object DoobieMetrics {
  def of[F[_]: Monad](
    collectorRegistry: CollectorRegistry[F],
    prefix: String = "db",
  ): Resource[F, String => DoobieMetrics[F]] = {

    val queryTimeSummary = collectorRegistry.summary(
      name = s"${ prefix }_query_time",
      help = s"Query time in seconds",
      quantiles = Quantiles.Default,
      labels = LabelNames("name", "result"),
    )

    val queryResultCounter = collectorRegistry.counter(
      name = s"${ prefix }_query_result",
      help = "Query result: success or failure",
      labels = LabelNames("name", "result"),
    )

    for {
      queryTimeSummary <- queryTimeSummary
      queryResultCounter <- queryResultCounter
    } yield { (name: String) =>
      def result(success: Boolean) = if (success) "success" else "failure"
      new DoobieMetrics[F] {
        override def query(time: FiniteDuration, success: Boolean): F[Unit] =
          for {
            _ <- queryResultCounter.labels(name, result(success)).inc()
            _ <- queryTimeSummary.labels(name, result(success)).observe(time.toNanos.nanosToSeconds)
          } yield ()
      }
    }
  }
}
