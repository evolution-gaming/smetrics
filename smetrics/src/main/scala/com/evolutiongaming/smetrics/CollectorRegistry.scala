package com.evolutiongaming.smetrics

import cats.Monad
import cats.effect.syntax.all.*
import cats.effect.{Concurrent, Resource}
import cats.syntax.all.*
import com.evolutiongaming.catshelper.{ResourceCounter, SerialRef}

trait CollectorRegistry[F[_]] {

  def gauge[A, B[_]](
    name: String,
    help: String,
    labels: A,
  )(implicit
    magnet: LabelsMagnet[A, B],
  ): Resource[F, B[Gauge[F]]]

  def gaugeInitialized[A, B[_]](
    name: String,
    help: String,
    labels: A,
  )(implicit
    magnet: LabelsMagnetInitialized[A, B],
  ): Resource[F, B[Gauge[F]]]

  def counter[A, B[_]](
    name: String,
    help: String,
    labels: A,
  )(implicit
    magnet: LabelsMagnet[A, B],
  ): Resource[F, B[Counter[F]]]

  def counterInitialized[A, B[_]](
    name: String,
    help: String,
    labels: A,
  )(implicit
    magnet: LabelsMagnetInitialized[A, B],
  ): Resource[F, B[Counter[F]]]

  def summary[A, B[_]](
    name: String,
    help: String,
    quantiles: Quantiles,
    labels: A,
  )(implicit
    magnet: LabelsMagnet[A, B],
  ): Resource[F, B[Summary[F]]]

  def summaryInitialized[A, B[_]](
    name: String,
    help: String,
    quantiles: Quantiles,
    labels: A,
  )(implicit
    magnet: LabelsMagnetInitialized[A, B],
  ): Resource[F, B[Summary[F]]]

  def histogram[A, B[_]](
    name: String,
    help: String,
    buckets: Buckets,
    labels: A,
  )(implicit
    magnet: LabelsMagnet[A, B],
  ): Resource[F, B[Histogram[F]]]

  def histogramInitialized[A, B[_]](
    name: String,
    help: String,
    buckets: Buckets,
    labels: A,
  )(implicit
    magnet: LabelsMagnetInitialized[A, B],
  ): Resource[F, B[Histogram[F]]]

  def info[A, B[_]](
    name: String,
    help: String,
    labels: A,
  )(implicit
    magnet: LabelsMagnet[A, B],
  ): Resource[F, B[Info[F]]]

  def prefixed(prefix: String): CollectorRegistry[F] = new CollectorRegistry.Prefixed[F](this, prefix)

  def withCaching(
    implicit
    F: Concurrent[F],
  ): F[CollectorRegistry[F]] =
    for {
      cache <- SerialRef.of[F, CollectorRegistry.Cached.State[F]](Map.empty)
    } yield new CollectorRegistry.Cached(this, cache)
}

object CollectorRegistry {

  def empty[F[_]: Monad]: CollectorRegistry[F] = {
    const[F](
      Gauge.empty[F].pure[F],
      Counter.empty[F].pure[F],
      Summary.empty[F].pure[F],
      Histogram.empty[F].pure[F],
      Info.empty[F].pure[F],
    )
  }

  def const[F[_]: Monad](
    gauge: F[Gauge[F]],
    counter: F[Counter[F]],
    summary: F[Summary[F]],
    histogram: F[Histogram[F]],
    info: F[Info[F]],
  ): CollectorRegistry[F] = {

    val gauge1 = gauge

    val counter1 = counter

    val summary1 = summary

    val histogram1 = histogram

    val info1 = info

    def apply[A, B[_], C](
      collector: F[C],
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[F, B[C]] = {
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
        labels: A,
      )(implicit
        magnet: LabelsMagnet[A, B],
      ): Resource[F, B[Gauge[F]]] = {
        apply(gauge1)
      }

      def gaugeInitialized[A, B[_]](
        name: String,
        help: String,
        labels: A,
      )(implicit
        magnet: LabelsMagnetInitialized[A, B],
      ): Resource[F, B[Gauge[F]]] = {
        apply(gauge1)
      }

      def counter[A, B[_]](
        name: String,
        help: String,
        labels: A,
      )(implicit
        magnet: LabelsMagnet[A, B],
      ): Resource[F, B[Counter[F]]] = {
        apply(counter1)
      }

      def counterInitialized[A, B[_]](
        name: String,
        help: String,
        labels: A,
      )(implicit
        magnet: LabelsMagnetInitialized[A, B],
      ): Resource[F, B[Counter[F]]] = {
        apply(counter1)
      }

      def summary[A, B[_]](
        name: String,
        help: String,
        quantiles: Quantiles,
        labels: A,
      )(implicit
        magnet: LabelsMagnet[A, B],
      ): Resource[F, B[Summary[F]]] = {
        apply(summary1)
      }

      def summaryInitialized[A, B[_]](
        name: String,
        help: String,
        quantiles: Quantiles,
        labels: A,
      )(implicit
        magnet: LabelsMagnetInitialized[A, B],
      ): Resource[F, B[Summary[F]]] = {
        apply(summary1)
      }

      def histogram[A, B[_]](
        name: String,
        help: String,
        buckets: Buckets,
        labels: A,
      )(implicit
        magnet: LabelsMagnet[A, B],
      ): Resource[F, B[Histogram[F]]] = {
        apply(histogram1)
      }

      def histogramInitialized[A, B[_]](
        name: String,
        help: String,
        buckets: Buckets,
        labels: A,
      )(implicit
        magnet: LabelsMagnetInitialized[A, B],
      ): Resource[F, B[Histogram[F]]] = {
        apply(histogram1)
      }

      override def info[A, B[_]](
        name: String,
        help: String,
        labels: A,
      )(implicit
        magnet: LabelsMagnet[A, B],
      ): Resource[F, B[Info[F]]] = {
        apply(info1)
      }
    }
  }

  private class Prefixed[F[_]](private val delegate: CollectorRegistry[F], private val prefix: String)
  extends CollectorRegistry[F] {

    private def prefixedName(name: String): String = s"${ prefix }_$name"

    def gauge[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[F, B[Gauge[F]]] = delegate.gauge(prefixedName(name), help, labels)

    def gaugeInitialized[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[F, B[Gauge[F]]] = delegate.gaugeInitialized(prefixedName(name), help, labels)

    def counter[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[F, B[Counter[F]]] = delegate.counter(prefixedName(name), help, labels)

    def counterInitialized[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[F, B[Counter[F]]] = delegate.counterInitialized(prefixedName(name), help, labels)

    def summary[A, B[_]](
      name: String,
      help: String,
      quantiles: Quantiles,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[F, B[Summary[F]]] = delegate.summary(prefixedName(name), help, quantiles, labels)

    def summaryInitialized[A, B[_]](
      name: String,
      help: String,
      quantiles: Quantiles,
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[F, B[Summary[F]]] = delegate.summaryInitialized(prefixedName(name), help, quantiles, labels)

    def histogram[A, B[_]](
      name: String,
      help: String,
      buckets: Buckets,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[F, B[Histogram[F]]] = delegate.histogram(prefixedName(name), help, buckets, labels)

    def histogramInitialized[A, B[_]](
      name: String,
      help: String,
      buckets: Buckets,
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[F, B[Histogram[F]]] = delegate.histogramInitialized(prefixedName(name), help, buckets, labels)

    override def info[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[F, B[Info[F]]] = delegate.info(prefixedName(name), help, labels)

  }

  private[smetrics] object Cached {
    case class Entry[F[_]](rc: ResourceCounter[F, ?], names: List[String], ofType: String)

    type State[F[_]] = Map[String, Entry[F]]
  }

  private[smetrics] class Cached[F[_]: Concurrent](
    registry: CollectorRegistry[F],
    stateRef: SerialRef[F, Cached.State[F]],
  ) extends CollectorRegistry[F] {

    import Cached.*

    private def getOrCreate[A](
      name: String,
      names: List[String],
      ofType: String,
      create: Resource[F, A],
    ): Resource[F, A] = {

      type Modified = (State[F], ResourceCounter[F, A])

      def getOrCreate1(state: State[F]): F[Modified] =
        state.get(name) match {

          case Some(entry) =>

            if (entry.ofType != ofType) {

              val message =
                s"metric `$name` of type `${ entry.ofType }` with labels [${ entry.names.mkString(", ") }] " +
                  s"already registered, while new metric of type `$ofType` tried to be created"
              new IllegalArgumentException(message).raiseError[F, Modified]

            } else if (entry.names != names) {

              val message =
                s"metric `$name` of type `${ entry.ofType }` with labels [${ entry.names.mkString(", ") }] " +
                  s"already registered, while new metric tried to be created with labels [${ names.mkString(", ") }]"
              new IllegalArgumentException(message).raiseError[F, Modified]

            } else {

              Concurrent[F]
                .catchNonFatal {
                  state -> entry.rc.asInstanceOf[ResourceCounter[F, A]]
                }
                .recoverWith { case cause =>
                  val message =
                    s"metric `$name` of type `${ entry.ofType }` with labels [${ entry.names.mkString(", ") }] " +
                      s"already registered and cannot be cast to type `$ofType` with labels [${ names.mkString(", ") }]"
                  new IllegalArgumentException(message, cause).raiseError[F, Modified]
                }

            }

          case None =>
            val invalidate = stateRef.update { cache => (cache - name).pure[F] }
            val resource = create.onFinalize(invalidate)
            for {
              rc <- ResourceCounter.of[F, A](resource)
              s1 = state.updated(name, Entry(rc, names, ofType))
            } yield s1 -> rc
        }

      for {
        rc <- stateRef.modify(getOrCreate1).toResource
        a <- rc.resource
      } yield a
    }

    override def gauge[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[F, B[Gauge[F]]] =
      getOrCreate(
        name = name,
        names = magnet.names(labels),
        ofType = "gauge",
        create = registry.gauge(name, help, labels)(magnet),
      )

    override def gaugeInitialized[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[F, B[Gauge[F]]] =
      getOrCreate(
        name = name,
        names = magnet.names(labels),
        ofType = "gauge",
        create = registry.gauge(name, help, labels)(magnet),
      )

    override def counter[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[F, B[Counter[F]]] =
      getOrCreate(
        name = name,
        names = magnet.names(labels),
        ofType = "counter",
        create = registry.counter(name, help, labels)(magnet),
      )

    override def counterInitialized[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[F, B[Counter[F]]] =
      getOrCreate(
        name = name,
        names = magnet.names(labels),
        ofType = "counter",
        create = registry.counter(name, help, labels)(magnet),
      )

    override def summary[A, B[_]](
      name: String,
      help: String,
      quantiles: Quantiles,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[F, B[Summary[F]]] =
      getOrCreate(
        name = name,
        names = magnet.names(labels),
        ofType = "summary",
        create = registry.summary(name, help, quantiles, labels)(magnet),
      )

    override def summaryInitialized[A, B[_]](
      name: String,
      help: String,
      quantiles: Quantiles,
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[F, B[Summary[F]]] =
      getOrCreate(
        name = name,
        names = magnet.names(labels),
        ofType = "summary",
        create = registry.summary(name, help, quantiles, labels)(magnet),
      )

    override def histogram[A, B[_]](
      name: String,
      help: String,
      buckets: Buckets,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[F, B[Histogram[F]]] =
      getOrCreate(
        name = name,
        names = magnet.names(labels),
        ofType = "histogram",
        create = registry.histogram(name, help, buckets, labels)(magnet),
      )

    override def histogramInitialized[A, B[_]](
      name: String,
      help: String,
      buckets: Buckets,
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[F, B[Histogram[F]]] =
      getOrCreate(
        name = name,
        names = magnet.names(labels),
        ofType = "histogram",
        create = registry.histogram(name, help, buckets, labels)(magnet),
      )

    override def info[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[F, B[Info[F]]] =
      getOrCreate(
        name = name,
        names = magnet.names(labels),
        ofType = "info",
        create = registry.info(name, help, labels)(magnet),
      )
  }

}
