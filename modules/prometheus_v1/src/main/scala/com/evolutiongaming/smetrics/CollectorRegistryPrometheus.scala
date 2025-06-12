package com.evolutiongaming.smetrics

import cats.effect._
import cats.effect.syntax.all._
import cats.syntax.all._
import com.evolutiongaming.smetrics.CollectionHelper._
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.core.{metrics => P}

object CollectorRegistryPrometheus {

  def apply[F[_]: Sync](collectorRegistry: PrometheusRegistry): CollectorRegistry[F] = {

    def initializeLabelValues[M <: P.MetricWithFixedMetadata](
      metric:             M,
      initialLabelValues: List[List[String]],
    )(implicit adapter: PrometheusMetricAdapter[M, _]): Resource[F, Unit] =
      if (initialLabelValues.nonEmpty) {
        val combinations = initialLabelValues.combine
        Sync[F].delay {
          combinations.foreach { labelValues =>
            adapter.initializeLabelValues(metric, labelValues)
          }
        }.toResource
      } else Resource.unit

    def build[M <: P.MetricWithFixedMetadata](
      builder: P.MetricWithFixedMetadata.Builder[_, M],
    ): Resource[F, M] =
      Sync[F].delay(builder.build()).toResource

    def register[M <: P.Metric](m: M): Resource[F, M] = {
      val result = for {
        _ <- Sync[F].delay(collectorRegistry.register(m))
      } yield {
        val release = Sync[F].delay(collectorRegistry.unregister(m))
        (m, release)
      }
      Resource(result)
    }

    def apply[A, B[_], M <: P.MetricWithFixedMetadata, R](
      builder:            P.MetricWithFixedMetadata.Builder[_, M],
      initialLabelValues: List[List[String]],
    )(implicit
      adapter: PrometheusMetricAdapter[M, R],
      magnet:  LabelsMagnet[A, B],
    ): Resource[F, B[R]] =

      for {
        metric <- build(builder)
        _      <- initializeLabelValues(metric, initialLabelValues)
        metric <- register(metric)
      } yield magnet.withValues { labelValues =>
        adapter.toWrapped(metric, labelValues)
      }

    new CollectorRegistry[F] {

      def gauge[A, B[_]](name: String, help: String, labels: A)(implicit
        magnet: LabelsMagnet[A, B],
      ) = {
        val gauge = P.Gauge
          .builder()
          .name(name)
          .help(help)
          .labelNames(magnet.names(labels): _*)

        apply(gauge, List.empty)
      }

      def gaugeInitialized[A, B[_]](name: String, help: String, labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B],
      ) = {
        val gauge = P.Gauge
          .builder()
          .name(name)
          .help(help)
          .labelNames(magnet.names(labels): _*)

        apply(gauge, magnet.values(labels))
      }

      def counter[A, B[_]](name: String, help: String, labels: A)(implicit
        magnet: LabelsMagnet[A, B],
      ) = {
        val counter = P.Counter
          .builder()
          .name(name)
          .help(help)
          .labelNames(magnet.names(labels): _*)

        apply(counter, List.empty)
      }

      def counterInitialized[A, B[_]](name: String, help: String, labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B],
      ) = {
        val counter = P.Counter
          .builder()
          .name(name)
          .help(help)
          .labelNames(magnet.names(labels): _*)

        apply(counter, magnet.values(labels))
      }

      def summary[A, B[_]](name: String, help: String, quantiles: Quantiles, labels: A)(implicit
        magnet: LabelsMagnet[A, B],
      ) = {
        val summary = {
          val summary = P.Summary
            .builder()
            .name(name)
            .help(help)
            .labelNames(magnet.names(labels): _*)

          quantiles.values
            .foldLeft(summary)((summary, quantile) => summary.quantile(quantile.value, quantile.error))
        }

        apply(summary, List.empty)
      }

      def summaryInitialized[A, B[_]](name: String, help: String, quantiles: Quantiles, labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B],
      ) = {
        val summary = {
          val summary = P.Summary
            .builder()
            .name(name)
            .help(help)
            .labelNames(magnet.names(labels): _*)

          quantiles.values
            .foldLeft(summary)((summary, quantile) => summary.quantile(quantile.value, quantile.error))
        }

        apply(summary, magnet.values(labels))
      }

      def histogram[A, B[_]](name: String, help: String, buckets: Buckets, labels: A)(implicit
        magnet: LabelsMagnet[A, B],
      ) = {

        val histogram = P.Histogram
          .builder()
          .name(name)
          .help(help)
          .classicUpperBounds(buckets.values.toList: _*)
          .labelNames(magnet.names(labels): _*)

        apply(histogram, List.empty)
      }

      def histogramInitialized[A, B[_]](name: String, help: String, buckets: Buckets, labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B],
      ) = {

        val histogram = P.Histogram
          .builder()
          .name(name)
          .help(help)
          .classicUpperBounds(buckets.values.toList: _*)
          .labelNames(magnet.names(labels): _*)

        apply(histogram, magnet.values(labels))
      }
    }
  }

  private trait PrometheusMetricAdapter[M <: P.MetricWithFixedMetadata, R] {
    def initializeLabelValues(m: M, labels: List[String]): Unit
    def toWrapped(m:             M, labels: List[String]): R
  }

  implicit private def counterAdapter[F[_]: Sync]: PrometheusMetricAdapter[P.Counter, Counter[F]] =
    new PrometheusMetricAdapter[P.Counter, Counter[F]] {
      def initializeLabelValues(m: P.Counter, labels: List[String]): Unit =
        m.initLabelValues(labels: _*)
      def toWrapped(m: P.Counter, labels: List[String]): Counter[F] =
        new Counter[F] {
          def inc(delta: Double): F[Unit] = Sync[F].delay {
            if (labels.isEmpty) m.inc(delta)
            else m.labelValues(labels: _*).inc(delta)
          }
        }
    }

  implicit private def gaugeAdapter[F[_]: Sync]: PrometheusMetricAdapter[P.Gauge, Gauge[F]] =
    new PrometheusMetricAdapter[P.Gauge, Gauge[F]] {
      def initializeLabelValues(m: P.Gauge, labels: List[String]): Unit =
        m.initLabelValues(labels: _*)
      def toWrapped(m: P.Gauge, labels: List[String]): Gauge[F] =
        new Gauge[F] {
          def inc(delta: Double): F[Unit] = Sync[F].delay {
            if (labels.isEmpty) m.inc(delta)
            else m.labelValues(labels: _*).inc(delta)
          }

          def dec(delta: Double) = Sync[F].delay {
            if (labels.isEmpty) m.dec(delta)
            else m.labelValues(labels: _*).dec(delta)
          }

          def set(value: Double) = Sync[F].delay {
            if (labels.isEmpty) m.set(value)
            else m.labelValues(labels: _*).set(value)
          }
        }
    }

  implicit private def histogramAdapter[F[_]: Sync]: PrometheusMetricAdapter[P.Histogram, Histogram[F]] =
    new PrometheusMetricAdapter[P.Histogram, Histogram[F]] {
      def initializeLabelValues(m: P.Histogram, labels: List[String]): Unit =
        m.initLabelValues(labels: _*)
      def toWrapped(m: P.Histogram, labels: List[String]): Histogram[F] =
        new Histogram[F] {
          def observe(value: Double) = Sync[F].delay {
            if (labels.isEmpty) m.observe(value)
            else m.labelValues(labels: _*).observe(value)
          }
        }
    }
  implicit private def summaryAdapter[F[_]: Sync]: PrometheusMetricAdapter[P.Summary, Summary[F]] =
    new PrometheusMetricAdapter[P.Summary, Summary[F]] {
      def initializeLabelValues(m: P.Summary, labels: List[String]): Unit =
        m.initLabelValues(labels: _*)
      def toWrapped(m: P.Summary, labels: List[String]): Summary[F] =
        new Summary[F] {
          def observe(value: Double) = Sync[F].delay {
            if (labels.isEmpty) m.observe(value)
            else m.labelValues(labels: _*).observe(value)
          }
        }
    }
}
