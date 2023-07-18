package com.evolutiongaming.smetrics

import cats.effect.kernel.Resource
import java.io.StringWriter
import cats.effect.{Async, Sync}
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.{client => P}

trait Prometheus[F[_]] {

  def registry: CollectorRegistry[F]

  def write004: F[String]
}

object Prometheus { prometheus =>

  def apply[F[_] : Sync](collectorRegistry: P.CollectorRegistry): Prometheus[F] =
    new Prometheus[F] {

      override val registry: CollectorRegistry[F] = CollectorRegistryPrometheus(collectorRegistry)

      override val write004: F[String] = prometheus.write004[F](collectorRegistry)
    }

  def cached[F[_] : Async](javaRegistry: P.CollectorRegistry): Resource[F, Prometheus[F]] =
    for {
      cachedRegistry <- CollectorRegistryPrometheus(javaRegistry).cached
    } yield new Prometheus[F] {

      override val registry: CollectorRegistry[F] = cachedRegistry

      override val write004: F[String] = prometheus.write004[F](javaRegistry)
    }

  def default[F[_] : Sync]: Prometheus[F] = apply(P.CollectorRegistry.defaultRegistry)

  private def write004[F[_] : Sync](registry: P.CollectorRegistry): F[String] = Sync[F].delay {
    val writer = new StringWriter
    TextFormat.write004(writer, registry.metricFamilySamples)
    writer.toString
  }

}