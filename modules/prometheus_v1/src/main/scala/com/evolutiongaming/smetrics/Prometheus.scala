package com.evolutiongaming.smetrics

import cats.effect._
import cats.syntax.all._

import io.prometheus.metrics.model.registry.PrometheusRegistry
import java.io.ByteArrayOutputStream
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter

trait Prometheus[F[_]] {

  def registry: CollectorRegistry[F]

  def write004: F[String]
}

object Prometheus { prometheus =>

  def apply[F[_]: Sync](collectorRegistry: PrometheusRegistry): Prometheus[F] =
    new Prometheus[F] {

      override val registry: CollectorRegistry[F] = CollectorRegistryPrometheus(collectorRegistry)

      override val write004: F[String] = Sync[F].delay {
        val out              = new ByteArrayOutputStream()
        val textFormatWriter = PrometheusTextFormatWriter.builder().setIncludeCreatedTimestamps(false).build()
        textFormatWriter.write(out, collectorRegistry.scrape())
        out.toString("UTF-8")
      }
    }

  def default[F[_]: Sync]: Prometheus[F] = apply(PrometheusRegistry.defaultRegistry)

  implicit class Ops[F[_]](val prometheus: Prometheus[F]) extends AnyVal {

    def withCaching(implicit F: Concurrent[F]): F[Prometheus[F]] =
      prometheus.registry.withCaching.map { cachedRegistry =>
        new Prometheus[F] {
          override def registry: CollectorRegistry[F] = cachedRegistry

          override def write004: F[String] = prometheus.write004
        }
      }

  }

}
