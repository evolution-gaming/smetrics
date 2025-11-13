package sttp.client3.smetrics

// import cats._
// import cats.syntax.all._
import cats.effect._
// import cats.effect.syntax.all._
import com.evolutiongaming.smetrics._
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import cats.effect.Ref

case class MetricEvent(metricType: String, name: String, labels: List[String], op: String, value: Double)

class InMemoryCollectorRegistry(ref: Ref[IO, Vector[MetricEvent]]) extends CollectorRegistry[IO] {
  private def record(metricType: String, name: String, labels: List[String], op: String, value: Double): IO[Unit] =
    ref.update(events => events :+ MetricEvent(metricType, name, labels, op, value))

  override def counter[A, B[_]](
      name: String,
      help: String,
      labels: A
  )(implicit magnet: LabelsMagnet[A, B]): Resource[IO, B[Counter[IO]]] =
    Resource.pure(magnet.withValues { labelValues =>
      new Counter[IO] {
        override def inc(value: Double): IO[Unit] = record("counter", name, labelValues, "inc", value)
      }
    })

  override def counterInitialized[A, B[_]](
      name: String,
      help: String,
      labels: A
  )(implicit magnet: LabelsMagnetInitialized[A, B]): Resource[IO, B[Counter[IO]]] =
    counter(name, help, labels)(magnet)

  override def gauge[A, B[_]](
      name: String,
      help: String,
      labels: A
  )(implicit magnet: LabelsMagnet[A, B]): Resource[IO, B[Gauge[IO]]] =
    Resource.pure(magnet.withValues { labelValues =>
      new Gauge[IO] {
        override def set(value: Double): IO[Unit] = record("gauge", name, labelValues, "set", value)
        override def inc(value: Double): IO[Unit] = record("gauge", name, labelValues, "inc", value)
        override def dec(value: Double): IO[Unit] = record("gauge", name, labelValues, "dec", value)
      }
    })

  override def gaugeInitialized[A, B[_]](
      name: String,
      help: String,
      labels: A
  )(implicit magnet: LabelsMagnetInitialized[A, B]): Resource[IO, B[Gauge[IO]]] =
    gauge(name, help, labels)(magnet)

  override def histogram[A, B[_]](
      name: String,
      help: String,
      buckets: Buckets,
      labels: A
  )(implicit magnet: LabelsMagnet[A, B]): Resource[IO, B[Histogram[IO]]] =
    Resource.pure(magnet.withValues { labelValues =>
      new Histogram[IO] {
        override def observe(value: Double): IO[Unit] = record("histogram", name, labelValues, "observe", value)
      }
    })

  override def histogramInitialized[A, B[_]](
      name: String,
      help: String,
      buckets: Buckets,
      labels: A
  )(implicit magnet: LabelsMagnetInitialized[A, B]): Resource[IO, B[Histogram[IO]]] =
    histogram(name, help, buckets, labels)(magnet)

  override def summary[A, B[_]](
      name: String,
      help: String,
      quantiles: Quantiles,
      labels: A
  )(implicit magnet: LabelsMagnet[A, B]): Resource[IO, B[Summary[IO]]] =
    Resource.pure(magnet.withValues { labelValues =>
      new Summary[IO] {
        override def observe(value: Double): IO[Unit] = record("summary", name, labelValues, "observe", value)
      }
    })

  override def summaryInitialized[A, B[_]](
      name: String,
      help: String,
      quantiles: Quantiles,
      labels: A
  )(implicit magnet: LabelsMagnetInitialized[A, B]): Resource[IO, B[Summary[IO]]] =
    summary(name, help, quantiles, labels)(magnet)
}

object InMemoryCollectorRegistry {
  def create: IO[(CollectorRegistry[IO], IO[Vector[MetricEvent]])] =
    Ref.of[IO, Vector[MetricEvent]](Vector.empty).map { ref =>
      (new InMemoryCollectorRegistry(ref), ref.get)
    }
}

class SmetricsBackendSpec extends AnyFunSuiteLike with Matchers {

  def inMemoryCollectorRegistry: CollectorRegistry[IO] = CollectorRegistry.empty[IO]
}
