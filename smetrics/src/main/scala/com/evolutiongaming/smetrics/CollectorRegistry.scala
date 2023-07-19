package com.evolutiongaming.smetrics

import cats.Monad
import cats.effect.Resource
import cats.effect.Concurrent
import cats.effect.implicits.effectResourceOps
import cats.implicits.*
import com.evolutiongaming.catshelper.SerialRef

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

  def cached(implicit F: Concurrent[F]): Resource[F, CollectorRegistry[F]] = CollectorRegistry.cached(this)
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

  final class CachedRegistryException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)

  private def cached[F[_] : Concurrent](registry: CollectorRegistry[F]): Resource[F, CollectorRegistry[F]] = {

    case class Entry(collector: Any, names: List[String], ofType: String)

    for {
      cache <- SerialRef.of[Resource[F, *], Map[String, Entry]](Map.empty)
    } yield new CollectorRegistry[F] {

      override def gauge[A, B[_]](name: String, help: String, labels: A)(implicit magnet: LabelsMagnet[A, B]): Resource[F, B[Gauge[F]]] =
        getOrCreate(
          name = name,
          names = magnet.names(labels),
          ofType = "gauge",
          create = registry.gauge(name, help, labels)(magnet),
        )

      override def gaugeInitialized[A, B[_]](name: String, help: String, labels: A)(implicit magnet: LabelsMagnetInitialized[A, B]): Resource[F, B[Gauge[F]]] =
        getOrCreate(
          name = name,
          names = magnet.names(labels),
          ofType = "gauge",
          create = registry.gauge(name, help, labels)(magnet),
        )

      override def counter[A, B[_]](name: String, help: String, labels: A)(implicit magnet: LabelsMagnet[A, B]): Resource[F, B[Counter[F]]] =
        getOrCreate(
          name = name,
          names = magnet.names(labels),
          ofType = "counter",
          create = registry.counter(name, help, labels)(magnet),
        )

      override def counterInitialized[A, B[_]](name: String, help: String, labels: A)(implicit magnet: LabelsMagnetInitialized[A, B]): Resource[F, B[Counter[F]]] =
        getOrCreate(
          name = name,
          names = magnet.names(labels),
          ofType = "counter",
          create = registry.counter(name, help, labels)(magnet),
        )

      override def summary[A, B[_]](name: String, help: String, quantiles: Quantiles, labels: A)(implicit magnet: LabelsMagnet[A, B]): Resource[F, B[Summary[F]]] =
        getOrCreate(
          name = name,
          names = magnet.names(labels),
          ofType = "summary",
          create = registry.summary(name, help, quantiles, labels)(magnet),
        )

      override def summaryInitialized[A, B[_]](name: String, help: String, quantiles: Quantiles, labels: A)(implicit magnet: LabelsMagnetInitialized[A, B]): Resource[F, B[Summary[F]]] =
        getOrCreate(
          name = name,
          names = magnet.names(labels),
          ofType = "summary",
          create = registry.summary(name, help, quantiles, labels)(magnet),
        )

      override def histogram[A, B[_]](name: String, help: String, buckets: Buckets, labels: A)(implicit magnet: LabelsMagnet[A, B]): Resource[F, B[Histogram[F]]] =
        getOrCreate(
          name = name,
          names = magnet.names(labels),
          ofType = "histogram",
          create = registry.histogram(name, help, buckets, labels)(magnet),
        )

      override def histogramInitialized[A, B[_]](name: String, help: String, buckets: Buckets, labels: A)(implicit magnet: LabelsMagnetInitialized[A, B]): Resource[F, B[Histogram[F]]] =
        getOrCreate(
          name = name,
          names = magnet.names(labels),
          ofType = "histogram",
          create = registry.histogram(name, help, buckets, labels)(magnet),
        )

      private def getOrCreate[A](
                                  name: String,
                                  names: List[String],
                                  ofType: String,
                                  create: Resource[F, A],
                                ): Resource[F, A] =
        cache.modify { cache =>
          cache.get(name) match {

            case Some(entry) =>

              if (entry.ofType != ofType) {

                val message =
                  s"metric `$name` of type `${entry.ofType}` with labels [${entry.names.mkString(", ")}] " +
                    s"already registered, while new metric of type `$ofType` tried to be created"
                new CachedRegistryException(message).raiseError[F, (Map[String, Entry], A)].toResource

              } else if (entry.names != names) {

                val message =
                  s"metric `$name` of type `${entry.ofType}` with labels [${entry.names.mkString(", ")}] " +
                    s"already registered, while new metric tried to be created with labels [${names.mkString(", ")}]"
                new CachedRegistryException(message).raiseError[F, (Map[String, Entry], A)].toResource

              } else {

                val cast = Concurrent[F]
                  .catchNonFatal {
                    entry.collector.asInstanceOf[A]
                  }
                  .recoverWith { case cause =>
                    val message =
                      s"metric `$name` of type `${entry.ofType}` with labels [${entry.names.mkString(", ")}] " +
                        s"already registered and cannot be cast to type `$ofType` with labels [${names.mkString(", ")}]"
                    new CachedRegistryException(message, cause).raiseError[F, A]
                  }
                for {
                  metric <- cast.toResource
                } yield cache -> metric

              }

            case None =>
              for {
                metric <- create
              } yield cache.updated(name, Entry(metric, names, ofType)) -> metric

          }
        }
    }
  }

}