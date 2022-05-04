package com.evolutiongaming.smetrics

import java.io.StringWriter

import cats.effect.Sync
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.{client => P}

trait Prometheus[F[_]] {

  def registry: CollectorRegistry[F]

  def write004: F[String]

  def initDefaults: F[Unit]
}

object Prometheus {

  def apply[F[_] : Sync](collectorRegistry: P.CollectorRegistry): Prometheus[F] =
    new Prometheus[F] {

      override val registry: CollectorRegistry[F] = CollectorRegistryPrometheus(collectorRegistry)

      override val write004: F[String] = Sync[F].delay {
        val writer = new StringWriter
        TextFormat.write004(writer, collectorRegistry.metricFamilySamples)
        writer.toString
      }

      override def initDefaults: F[Unit] = Sync[F].delay(io.prometheus.client.hotspot.DefaultExports.initialize())
    }

  def default[F[_] : Sync]: Prometheus[F] = apply(P.CollectorRegistry.defaultRegistry)
}