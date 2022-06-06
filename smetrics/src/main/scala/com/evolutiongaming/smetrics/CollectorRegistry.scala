package com.evolutiongaming.smetrics

import cats.Monad
import cats.effect.Resource
import cats.implicits._

trait CollectorRegistry[F[_]] {

  def gauge[A, B[_]](
    name: String,
    help: String,
    labels: A)(implicit
    magnet: LabelsMagnet[A, B]
  ): Resource[F, B[Gauge[F]]]

  def gaugeInitialized[A, B[_]](
    name: String,
    help: String,
    labels: A)(implicit
    magnet: LabelsMagnetInitialized[A, B]
  ): Resource[F, B[Gauge[F]]]


  def counter[A, B[_]](
    name: String,
    help: String,
    labels: A)(implicit
    magnet: LabelsMagnet[A, B]
  ): Resource[F, B[Counter[F]]]

  def counterInitialized[A, B[_]](
    name: String,
    help: String,
    labels: A)(implicit
    magnet: LabelsMagnetInitialized[A, B]
  ): Resource[F, B[Counter[F]]]


  def summary[A, B[_]](
    name: String,
    help: String,
    quantiles: Quantiles,
    labels: A)(implicit
    magnet: LabelsMagnet[A, B]
  ): Resource[F, B[Summary[F]]]

  def summaryInitialized[A, B[_]](
    name: String,
    help: String,
    quantiles: Quantiles,
    labels: A)(implicit
    magnet: LabelsMagnetInitialized[A, B]
  ): Resource[F, B[Summary[F]]]


  def histogram[A, B[_]](
    name: String,
    help: String,
    buckets: Buckets,
    labels: A)(implicit
    magnet: LabelsMagnet[A, B]
  ): Resource[F, B[Histogram[F]]]

  def histogramInitialized[A, B[_]](
    name: String,
    help: String,
    buckets: Buckets,
    labels: A)(implicit
    magnet: LabelsMagnetInitialized[A, B]
  ): Resource[F, B[Histogram[F]]]

  def prefixed(prefix: String): CollectorRegistry[F] = new CollectorRegistry.Prefixed[F](this, prefix)
}

object CollectorRegistry {

  def empty[F[_] : Monad]: CollectorRegistry[F] = {
    const[F](
      Gauge.empty[F].pure[F],
      Counter.empty[F].pure[F],
      Summary.empty[F].pure[F],
      Histogram.empty[F].pure[F])
  }

  def const[F[_] : Monad](
    gauge: F[Gauge[F]],
    counter: F[Counter[F]],
    summary: F[Summary[F]],
    histogram: F[Histogram[F]]
  ): CollectorRegistry[F] = {

    val gauge1 = gauge

    val counter1 = counter

    val summary1 = summary

    val histogram1 = histogram

    def apply[A, B[_], C](collector: F[C])(implicit magnet: LabelsMagnet[A, B]) = {
      val result = for {
        collector <- collector
      } yield {
        magnet.withValues { _ => collector }
      }
      Resource.eval(result)
    }

    new CollectorRegistry[F] {

      def gauge[A, B[_]](
        name: String,
        help: String,
        labels: A)(implicit
        magnet: LabelsMagnet[A, B]
      ) = {
        apply(gauge1)
      }

      def gaugeInitialized[A, B[_]](
        name: String,
        help: String,
        labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B]
      ) = {
        apply(gauge1)
      }

      def counter[A, B[_]](
        name: String,
        help: String,
        labels: A)(implicit
        magnet: LabelsMagnet[A, B]
      ) = {
        apply(counter1)
      }

      def counterInitialized[A, B[_]](
        name: String,
        help: String,
        labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B]
      ) = {
        apply(counter1)
      }

      def summary[A, B[_]](
        name: String,
        help: String,
        quantiles: Quantiles,
        labels: A)(implicit
        magnet: LabelsMagnet[A, B]
      ) = {
        apply(summary1)
      }

      def summaryInitialized[A, B[_]](
        name: String,
        help: String,
        quantiles: Quantiles,
        labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B]
      ) = {
        apply(summary1)
      }

      def histogram[A, B[_]](
        name: String,
        help: String,
        buckets: Buckets,
        labels: A)(implicit
        magnet: LabelsMagnet[A, B]
      ) = {
        apply(histogram1)
      }

      def histogramInitialized[A, B[_]](
        name: String,
        help: String,
        buckets: Buckets,
        labels: A)(implicit
        magnet: LabelsMagnetInitialized[A, B]
      ) = {
        apply(histogram1)
      }
    }
  }

  private class Prefixed[F[_]](private val delegate: CollectorRegistry[F], private val prefix: String)
    extends CollectorRegistry[F] {

    private def prefixedName(name: String): String = s"${prefix}_$name"

    def gauge[A, B[_]](
      name: String,
      help: String,
      labels: A)(implicit
      magnet: LabelsMagnet[A, B]
    ): Resource[F, B[Gauge[F]]] = delegate.gauge(prefixedName(name), help, labels)

    def gaugeInitialized[A, B[_]](
      name: String,
      help: String,
      labels: A)(implicit
      magnet: LabelsMagnetInitialized[A, B]
    ): Resource[F, B[Gauge[F]]] = delegate.gaugeInitialized(prefixedName(name), help, labels)


    def counter[A, B[_]](
      name: String,
      help: String,
      labels: A)(implicit
      magnet: LabelsMagnet[A, B]
    ): Resource[F, B[Counter[F]]] = delegate.counter(prefixedName(name), help, labels)

    def counterInitialized[A, B[_]](
      name: String,
      help: String,
      labels: A)(implicit
      magnet: LabelsMagnetInitialized[A, B]
    ): Resource[F, B[Counter[F]]] = delegate.counterInitialized(prefixedName(name), help, labels)


    def summary[A, B[_]](
      name: String,
      help: String,
      quantiles: Quantiles,
      labels: A)(implicit
      magnet: LabelsMagnet[A, B]
    ): Resource[F, B[Summary[F]]] = delegate.summary(prefixedName(name), help, quantiles, labels)

    def summaryInitialized[A, B[_]](
      name: String,
      help: String,
      quantiles: Quantiles,
      labels: A)(implicit
      magnet: LabelsMagnetInitialized[A, B]
    ): Resource[F, B[Summary[F]]] = delegate.summaryInitialized(prefixedName(name), help, quantiles, labels)


    def histogram[A, B[_]](
      name: String,
      help: String,
      buckets: Buckets,
      labels: A)(implicit
      magnet: LabelsMagnet[A, B]
    ): Resource[F, B[Histogram[F]]] = delegate.histogram(prefixedName(name), help, buckets, labels)

    def histogramInitialized[A, B[_]](
      name: String,
      help: String,
      buckets: Buckets,
      labels: A)(implicit
      magnet: LabelsMagnetInitialized[A, B]
    ): Resource[F, B[Histogram[F]]] = delegate.histogramInitialized(prefixedName(name), help, buckets, labels)
  }
}