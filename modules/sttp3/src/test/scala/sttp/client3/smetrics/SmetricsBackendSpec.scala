package sttp.client3.smetrics

// import cats._
// import cats.syntax.all._
import cats.effect._
import org.scalatest.funsuite.AsyncFunSuite
import sttp.client3.smetrics.SmetricsBackend.MetricNames
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
// import cats.effect.syntax.all._
import com.evolutiongaming.smetrics._
// import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import cats.effect.Ref
import com.evolutiongaming.smetrics.IOSuite._
import sttp.client3.impl.cats.implicits._

case class MetricEvent(name: String, metricType: String, labels: List[String], op: String, value: Double)

class InMemoryCollectorRegistry(state: Ref[IO, Vector[MetricEvent]]) extends CollectorRegistry[IO] {

  def events: IO[Vector[MetricEvent]] = state.get

  private def record(name: String, metricType: String, labels: List[String], op: String, value: Double): IO[Unit] =
    state.update(events => events :+ MetricEvent(name, metricType, labels, op, value))

  override def counter[A, B[_]](
      name: String,
      help: String,
      labels: A
  )(implicit magnet: LabelsMagnet[A, B]): Resource[IO, B[Counter[IO]]] =
    Resource.pure {
      magnet.withValues { labelValues =>
        new Counter[IO] {
          override def inc(value: Double): IO[Unit] = record(name, "counter", labelValues, "inc", value)
        }
      }
    }

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
    Resource.pure {
      magnet.withValues { labelValues =>
        new Gauge[IO] {
          override def set(value: Double): IO[Unit] = record(name, "gauge", labelValues, "set", value)

          override def inc(value: Double): IO[Unit] = record(name, "gauge", labelValues, "inc", value)

          override def dec(value: Double): IO[Unit] = record(name, "gauge", labelValues, "dec", value)
        }
      }
    }

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
    Resource.pure {
      magnet.withValues { labelValues =>
        new Histogram[IO] {
          override def observe(value: Double): IO[Unit] = record(name, "histogram", labelValues, "observe", value)
        }
      }
    }

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
    Resource.pure {
      magnet.withValues { labelValues =>
        new Summary[IO] {
          override def observe(value: Double): IO[Unit] = record(name, "summary", labelValues, "observe", value)
        }
      }
    }

  override def summaryInitialized[A, B[_]](
      name: String,
      help: String,
      quantiles: Quantiles,
      labels: A
  )(implicit magnet: LabelsMagnetInitialized[A, B]): Resource[IO, B[Summary[IO]]] =
    summary(name, help, quantiles, labels)(magnet)
}

object InMemoryCollectorRegistry {
  def make: IO[InMemoryCollectorRegistry] =
    for {
      ref <- Ref.of[IO, Vector[MetricEvent]](Vector.empty)
    } yield new InMemoryCollectorRegistry(ref)
}

class SmetricsBackendSpec extends AsyncFunSuite with Matchers {

  def inMemoryCollectorRegistry: CollectorRegistry[IO] = CollectorRegistry.empty[IO]

  test("SmetricsBackend updates latency, in-progress, request size, response size for successful request") {
    val body                  = "request-body"
    val responseBody          = "response-body"
    val responseContentLength = responseBody.length
    val requestContentLength  = body.length
    val uri                   = uri"https://test.com"

    val stubBackend = SttpBackendStub[IO, Any](sttp.monad.MonadError[IO])
      .whenRequestMatches(_ => true)
      .thenRespond(
        Response(
          body = responseBody,
          code = StatusCode.Ok,
          statusText = "OK",
          headers = Seq(sttp.model.Header("Content-Length", responseContentLength.toString)),
        )
      )

    val test = for {
      registry          <- InMemoryCollectorRegistry.make
      backendAllocated  <- SmetricsBackend(
                             stubBackend,
                             registry,
                           ).allocated
      (backend, release) = backendAllocated
      _                 <- basicRequest.post(uri).body(body).send(backend)
      events            <- registry.events
      _                 <- release
    } yield {
      // Check latency metric
      events.exists(e =>
        e.name == MetricNames.latency() && e.metricType == "histogram" && e.op == "observe"
      ) shouldBe true
      // Check in-progress gauge inc/dec
      events.exists(e => e.name == MetricNames.inProgress() && e.metricType == "gauge" && e.op == "inc") shouldBe true
      events.exists(e => e.name == MetricNames.inProgress() && e.metricType == "gauge" && e.op == "dec") shouldBe true
      // Check request size summary
      events.exists(e =>
        e.name == MetricNames
          .requestSize() && e.metricType == "summary" && e.op == "observe" && e.value == requestContentLength
      ) shouldBe true
      // Check response size summary
      events.exists(e =>
        e.name == MetricNames
          .responseSize() && e.metricType == "summary" && e.op == "observe" && e.value == responseContentLength
      ) shouldBe true
      println(events)
    }
    test.run()
  }
}
