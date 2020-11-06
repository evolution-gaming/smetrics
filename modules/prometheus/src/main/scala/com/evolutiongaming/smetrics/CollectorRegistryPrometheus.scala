package com.evolutiongaming.smetrics

import cats.effect.{Resource, Sync}
import cats.syntax.all._
import com.evolutiongaming.smetrics.CollectionHelper._
import io.prometheus.client.Collector
import io.prometheus.{client => P}

object CollectorRegistryPrometheus {

  def apply[F[_] : Sync](collectorRegistry: P.CollectorRegistry): CollectorRegistry[F] = {

    def initializeLabelValues[Child, C <: P.SimpleCollector[Child]](
      collector: C,
      initialLabelValues: List[List[String]]
    ): Resource[F, Unit] =
      if (initialLabelValues.nonEmpty) {
        val combinations = initialLabelValues.combine
        Resource.liftF(
          Sync[F].delay { combinations.foreach(labelValues => collector.labels(labelValues: _*)) }
        )
      } else Resource.pure[F, Unit](())

    def build[Child, C <: P.SimpleCollector[Child], Builder <: P.SimpleCollector.Builder[Builder, C]](
      builder: Builder, labelNames: List[String]
    ): Resource[F, C] =
      Resource.liftF(
        Sync[F].delay { builder.labelNames(labelNames: _*).create() }
      )

    def register[A <: Collector](collector: A): Resource[F, A] = {
      val result = for {
        _ <- Sync[F].delay { collectorRegistry.register(collector) }
      } yield {
        val release = Sync[F].delay { collectorRegistry.unregister(collector) }
        (collector, release)
      }
      Resource(result)
    }

    def apply[A, B[_], Child, C <: P.SimpleCollector[Child], Builder <: P.SimpleCollector.Builder[Builder, C], R](
      builder: Builder,
      labelNames: List[String],
      initialLabelValues: List[List[String]])(implicit
      magnet: LabelsMagnet[A, B],
      fromCollector: C => R,
      fromCollectorChild: Child => R
    ): Resource[F, B[R]] = {

      for {
        collector <- build[Child, C, Builder](builder, labelNames)
        _ <- initializeLabelValues[Child, C](collector, initialLabelValues)
        collector <- register(collector)
      } yield {
        magnet.withValues { labelValues =>
          if (labelValues.isEmpty) collector.as[R]
          else collector.labels(labelValues: _*).as[R]
        }
      }
    }

    new CollectorRegistry[F] {

      def gauge[A, B[_]](
        name: String,
        help: String,
        labels: A)(implicit
        magnet: LabelsMagnet[A, B]
      ) = {
        val gauge = P.Gauge.build()
          .name(name)
          .help(help)

        apply[A, B, P.Gauge.Child, P.Gauge, P.Gauge.Builder, Gauge[F]](gauge, magnet.names(labels), List.empty)
      }

      def gaugeInitialized[A, B[_]](
        name: String,
        help: String,
        labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B]
      ) = {
        val gauge = P.Gauge.build()
          .name(name)
          .help(help)

        apply[A, B, P.Gauge.Child, P.Gauge, P.Gauge.Builder, Gauge[F]](gauge, magnet.names(labels), magnet.values(labels))
      }


      def counter[A, B[_]](
        name: String,
        help: String,
        labels: A)(implicit
        magnet: LabelsMagnet[A, B]
      ) = {
        val counter = P.Counter.build()
          .name(name)
          .help(help)

        apply[A, B, P.Counter.Child, P.Counter, P.Counter.Builder, Counter[F]](counter, magnet.names(labels), List.empty)
      }

      def counterInitialized[A, B[_]](
        name: String,
        help: String,
        labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B]
      ) = {
        val counter = P.Counter.build()
          .name(name)
          .help(help)

        apply[A, B, P.Counter.Child, P.Counter, P.Counter.Builder, Counter[F]](counter, magnet.names(labels), magnet.values(labels))
      }


      def summary[A, B[_]](
        name: String,
        help: String,
        quantiles: Quantiles,
        labels: A)(implicit
        magnet: LabelsMagnet[A, B]
      ) = {
        val summary = {
          val summary = P.Summary.build()
            .name(name)
            .help(help)

          quantiles
            .values
            .foldLeft(summary) { (summary, quantile) => summary.quantile(quantile.value, quantile.error) }
        }

        apply[A, B, P.Summary.Child, P.Summary, P.Summary.Builder, Summary[F]](summary, magnet.names(labels), List.empty)
      }

      def summaryInitialized[A, B[_]](
        name: String,
        help: String,
        quantiles: Quantiles,
        labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B]
      ) = {
        val summary = {
          val summary = P.Summary.build()
            .name(name)
            .help(help)

          quantiles
            .values
            .foldLeft(summary) { (summary, quantile) => summary.quantile(quantile.value, quantile.error) }
        }

        apply[A, B, P.Summary.Child, P.Summary, P.Summary.Builder, Summary[F]](summary, magnet.names(labels), magnet.values(labels))
      }


      def histogram[A, B[_]](
        name: String,
        help: String,
        buckets: Buckets,
        labels: A)(implicit
        magnet: LabelsMagnet[A, B]
      ) = {

        val histogram = P.Histogram.build()
          .name(name)
          .help(help)
          .buckets(buckets.values.toList: _ *)

        apply[A, B, P.Histogram.Child, P.Histogram, P.Histogram.Builder, Histogram[F]](histogram, magnet.names(labels), List.empty)
      }

      def histogramInitialized[A, B[_]](
        name: String,
        help: String,
        buckets: Buckets,
        labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B]
      ) = {

        val histogram = P.Histogram.build()
          .name(name)
          .help(help)
          .buckets(buckets.values.toList: _ *)

        apply[A, B, P.Histogram.Child, P.Histogram, P.Histogram.Builder, Histogram[F]](histogram, magnet.names(labels), magnet.values(labels))
      }
    }
  }


  implicit class IdOps[A](val self: A) extends AnyVal {

    def as[B](implicit ab: A => B): B = ab(self)
  }


  private implicit def counterPrometheusToCounter[F[_] : Sync]: P.Counter => Counter[F] = (a: P.Counter) => {
    new Counter[F] {
      def inc(value: Double) = Sync[F].delay { a.inc(value) }
    }
  }


  private implicit def counterChildPrometheusToCounter[F[_] : Sync]: P.Counter.Child => Counter[F] = (a: P.Counter.Child) => {
    new Counter[F] {
      def inc(value: Double) = Sync[F].delay { a.inc(value) }
    }
  }


  private implicit def gaugePrometheusToGauge[F[_] : Sync]: P.Gauge => Gauge[F] = (a: P.Gauge) => {
    new Gauge[F] {

      def inc(value: Double) = Sync[F].delay { a.inc(value) }

      def dec(value: Double) = Sync[F].delay { a.dec(value) }

      def set(value: Double) = Sync[F].delay { a.set(value) }
    }
  }


  private implicit def gaugeChildPrometheusToGauge[F[_] : Sync]: P.Gauge.Child => Gauge[F] = (a: P.Gauge.Child) => {
    new Gauge[F] {

      def inc(value: Double) = Sync[F].delay { a.inc(value) }

      def dec(value: Double) = Sync[F].delay { a.dec(value) }

      def set(value: Double) = Sync[F].delay { a.set(value) }
    }
  }


  private implicit def summeryPrometheusToSummery[F[_] : Sync]: P.Summary => Summary[F] = (a: P.Summary) => {
    new Summary[F] {
      def observe(value: Double) = Sync[F].delay { a.observe(value) }
    }
  }


  private implicit def summeryChildPrometheusToSummery[F[_] : Sync]: P.Summary.Child => Summary[F] = (a: P.Summary.Child) => {
    new Summary[F] {
      def observe(value: Double) = Sync[F].delay { a.observe(value) }
    }
  }


  private implicit def histogramPrometheusToHistogram[F[_] : Sync]: P.Histogram => Histogram[F] = (a: P.Histogram) => {
    new Histogram[F] {
      def observe(value: Double) = Sync[F].delay { a.observe(value) }
    }
  }


  private implicit def histogramChildPrometheusToHistogram[F[_] : Sync]: P.Histogram.Child => Histogram[F] = (a: P.Histogram.Child) => {
    new Histogram[F] {
      def observe(value: Double) = Sync[F].delay { a.observe(value) }
    }
  }
}