package com.evolutiongaming.smetrics

import cats.effect.{Concurrent, Sync}
import cats.syntax.all._
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.{client => P}

import java.io.StringWriter

trait Prometheus[F[_]] {

  def registry: CollectorRegistry[F]

  def write004: F[String]
}

object Prometheus { prometheus =>

  def apply[F[_]: Sync](collectorRegistry: P.CollectorRegistry): Prometheus[F] =
    new Prometheus[F] {

      override val registry: CollectorRegistry[F] = CollectorRegistryPrometheus(collectorRegistry)

      override val write004: F[String] = Sync[F].delay {
        val writer = new StringWriter
        TextFormat.write004(writer, collectorRegistry.metricFamilySamples)
        writer.toString
      }
    }

  def default[F[_]: Sync]: Prometheus[F] = apply(P.CollectorRegistry.defaultRegistry)

  implicit class Ops[F[_]](val prometheus: Prometheus[F]) extends AnyVal {

    def withCaching(
      implicit
      F: Concurrent[F],
    ): F[Prometheus[F]] =
      prometheus.registry.withCaching.map { cachedRegistry =>
        new Prometheus[F] {
          override def registry: CollectorRegistry[F] = cachedRegistry

          override def write004: F[String] = prometheus.write004
        }
      }

  }

}
