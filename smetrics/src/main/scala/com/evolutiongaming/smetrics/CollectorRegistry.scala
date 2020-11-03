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

  def gaugeWithInitialLabels[A, B[_], D[_]](
    name: String,
    help: String,
    labels: A)(implicit
    magnet: LabelsMagnetWithInitialLabels[A, B, D]
  ): D[Resource[F, B[Gauge[F]]]]

  def counter[A, B[_]](
    name: String,
    help: String,
    labels: A)(implicit
    magnet: LabelsMagnet[A, B]
  ): Resource[F, B[Counter[F]]]

  def counterWithInitialLabels[A, B[_], D[_]](
    name: String,
    help: String,
    labels: A)(implicit
    magnet: LabelsMagnetWithInitialLabels[A, B, D]
  ): D[Resource[F, B[Counter[F]]]]


  def summary[A, B[_]](
    name: String,
    help: String,
    quantiles: Quantiles,
    labels: A)(implicit
    magnet: LabelsMagnet[A, B]
  ): Resource[F, B[Summary[F]]]

  def summaryWithInitialLabels[A, B[_], D[_]](
    name: String,
    help: String,
    quantiles: Quantiles,
    labels: A)(implicit
    magnet: LabelsMagnetWithInitialLabels[A, B, D]
  ): D[Resource[F, B[Summary[F]]]]

  def histogram[A, B[_]](
    name: String,
    help: String,
    buckets: Buckets,
    labels: A)(implicit
    magnet: LabelsMagnet[A, B]
  ): Resource[F, B[Histogram[F]]]

  def histogramWithInitialLabels[A, B[_], D[_]](
    name: String,
    help: String,
    buckets: Buckets,
    labels: A)(implicit
    magnet: LabelsMagnetWithInitialLabels[A, B, D]
  ): D[Resource[F, B[Histogram[F]]]]
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

    def apply[A, B[_], D[_], C](collector: F[C])(implicit magnet: LabelsMagnet[A, B]) = {
      val result = for {
        collector <- collector
      } yield {
        magnet.withValues { _ => collector }
      }
      Resource.liftF(result)
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

      def gaugeWithInitialLabels[A, B[_], D[_]](
        name: String,
        help: String,
        labels: A)(implicit
        magnet: LabelsMagnetWithInitialLabels[A, B, D]
      ) = {
        magnet.withInitialLabelValues { _ =>
          apply(gauge1)
        }
      }

      def counter[A, B[_]](
        name: String,
        help: String,
        labels: A)(implicit
        magnet: LabelsMagnet[A, B]
      ) = {
        apply(counter1)
      }

      def counterWithInitialLabels[A, B[_], D[_]](
        name: String,
        help: String,
        labels: A)(implicit
        magnet: LabelsMagnetWithInitialLabels[A, B, D]
      ) = {
        magnet.withInitialLabelValues { _ =>
          apply(counter1)
        }
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

      def summaryWithInitialLabels[A, B[_], D[_]](
        name: String,
        help: String,
        quantiles: Quantiles,
        labels: A)(implicit
        magnet: LabelsMagnetWithInitialLabels[A, B, D]
      ) = {
        magnet.withInitialLabelValues { _ =>
          apply(summary1)
        }
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

      def histogramWithInitialLabels[A, B[_], D[_]](
        name: String,
        help: String,
        buckets: Buckets,
        labels: A)(implicit
        magnet: LabelsMagnetWithInitialLabels[A, B, D]
      ) = {
        magnet.withInitialLabelValues { _ =>
          apply(histogram1)
        }
      }
    }
  }
}