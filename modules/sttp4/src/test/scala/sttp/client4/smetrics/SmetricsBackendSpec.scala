package sttp.client4.smetrics

import cats.data.NonEmptyList
import cats.effect.*
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.evolutiongaming.catshelper.ToTry
import com.evolutiongaming.smetrics.*
import com.evolutiongaming.smetrics.IOSuite.*
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client4.*
import sttp.client4.impl.cats.implicits.*
import sttp.client4.smetrics.SmetricsBackend.{DefaultBuckets, MetricNames, methodLabel, statusLabel}
import sttp.client4.smetrics.SmetricsBackendSpec.*
import sttp.client4.testing.BackendStub
import sttp.client4.testing.ResponseStub
import sttp.model.{Header, StatusCode}

class SmetricsBackendSpec extends AsyncFunSuite with Matchers {

  implicit val tt: ToTry[IO] = ToTry.ioToTry

  def inMemoryCollectorRegistry: CollectorRegistry[IO] = CollectorRegistry.empty[IO]

  private val `[0, 0.1]` = Within(0, 0.1)

  def collect[A](
    stub: BackendStub[IO] => BackendStub[IO],
    send: Backend[IO] => IO[A],
  ): IO[Vector[MetricEvent]] = {
    for {
      registry <- InMemoryCollectorRegistry.make
      backendAllocated <- SmetricsBackend
        .default(
          stub(BackendStub[IO](sttp.monad.MonadError[IO])),
          registry,
        )
        .allocated
      (backend, release) = backendAllocated
      _ <- send(backend)
      events <- registry.events
      _ <- release
    } yield events
  }

  val `/` = uri"/"
  val body = "[]"
  val html = "<html/>"

  test("successful request") {
    collect(
      stub =>
        stub.whenAnyRequest
          .thenRespond(
            ResponseStub.adjust(
              body = html,
              code = StatusCode.Ok,
            ).withContentLength(html.length.toLong),
          ),
      backend => basicRequest.post(`/`).body(body).send(backend),
    ).map { events =>
      val `rspSize` = html.length.toDouble
      val `reqSize` = body.length.toDouble
      events.size shouldBe 6
      events.collect {
        case MetricEvent("http_client_request_size_bytes", "summary", List("POST"), "observe", `reqSize`) => 1
        case MetricEvent("http_client_requests_active", "gauge", List("POST"), "inc", 1.0) => 2
        case MetricEvent("http_client_request_duration_seconds", "histogram", List("POST"), "observe", `[0, 0.1]`(_)) =>
          3
        case MetricEvent("http_client_requests_active", "gauge", List("POST"), "dec", 1.0) => 4
        case MetricEvent("http_client_response_size_bytes", "summary", List("POST", "2xx"), "observe", `rspSize`) => 5
        case MetricEvent("http_client_requests_success", "counter", List("POST", "2xx"), "inc", 1.0) => 6
      } shouldBe List(1, 2, 3, 4, 5, 6)
    }.run()
  }

  test("error request") {
    val response = "Client or server error"

    def check(status: StatusCode) = {

      collect(
        stub =>
          stub.whenAnyRequest
            .thenRespond(
              ResponseStub.adjust(
                body = response,
                code = status,
              ).withContentLength(response.length.toLong),
            ),
        backend => basicRequest.post(`/`).body(body).send(backend),
      ).map { events =>
        val `rspSize` = response.length.toDouble
        val `reqSize` = body.length.toDouble
        val sts = s"${ status.code / 100 }xx"
        events.size shouldBe 6
        events.collect {
          case MetricEvent("http_client_request_size_bytes", "summary", List("POST"), "observe", `reqSize`) => 1
          case MetricEvent("http_client_requests_active", "gauge", List("POST"), "inc", 1.0) => 2
          case MetricEvent(
                "http_client_request_duration_seconds",
                "histogram",
                List("POST"),
                "observe",
                `[0, 0.1]`(_),
              ) => 3
          case MetricEvent("http_client_requests_active", "gauge", List("POST"), "dec", 1.0) => 4
          case MetricEvent("http_client_response_size_bytes", "summary", List("POST", `sts`), "observe", `rspSize`) => 5
          case MetricEvent("http_client_requests_error", "counter", List("POST", `sts`), "inc", 1.0) => 6
        } shouldBe List(1, 2, 3, 4, 5, 6)
      }
    }

    { check(StatusCode.NotFound) *> check(StatusCode.InternalServerError) }.run()
  }

  test("failure request") {
    collect(
      stub => stub.whenAnyRequest.thenThrow(new RuntimeException("Network error")),
      backend =>
        basicRequest
          .post(`/`)
          .body(body)
          .send(backend)
          .attempt,
    ).map { events =>
      val `body.length` = body.length.toDouble
      events.size shouldBe 5
      events.collect {
        case MetricEvent("http_client_request_size_bytes", "summary", List("POST"), "observe", `body.length`) => 1
        case MetricEvent("http_client_requests_active", "gauge", List("POST"), "inc", 1.0) => 2
        case MetricEvent("http_client_request_duration_seconds", "histogram", List("POST"), "observe", `[0, 0.1]`(_)) =>
          3
        case MetricEvent("http_client_requests_active", "gauge", List("POST"), "dec", 1.0) => 4
        case MetricEvent("http_client_requests_failure", "counter", List("POST"), "inc", 1.0) => 5
      } shouldBe List(1, 2, 3, 4, 5)
    }.run()
  }

  test("configure prefix") {
    runIO {
      val stubBackend = BackendStub[IO](sttp.monad.MonadError[IO]).whenAnyRequest.thenRespondOk()

      for {
        registry <- InMemoryCollectorRegistry.make
        backendAllocated <- SmetricsBackend
          .default(
            stubBackend,
            registry,
            prefix = Some("prefix_"),
          )
          .allocated
        (backend, release) = backendAllocated
        _ <- basicRequest
          .get(uri"/")
          .send(backend)
        events <- registry.events
        _ <- release
      } yield {
        events.nonEmpty shouldBe true
        events.forall(_.name.startsWith("prefix_")) shouldBe true
      }
    }
  }

  test("configure metrics labels") {
    runIO {
      val stubBackend = BackendStub[IO](sttp.monad.MonadError[IO]).whenAnyRequest.thenRespond(
        ResponseStub.adjust(
          body = html,
          code = StatusCode.Ok,
        ).withContentLength(html.length.toLong),
      )

      def label(name: String): String =
        s"labelFor$name"

      val backendLabel = label("Backend")
      val resourceLabel = label("Resource")

      val prefix = "client_"
      val resource = for {
        registry <- InMemoryCollectorRegistry.make.toResource
        duration <- registry.histogram(
          name = s"$prefix${ MetricNames.duration }",
          help = "Request duration in seconds",
          buckets = Buckets(NonEmptyList.fromListUnsafe(DefaultBuckets)),
          labels = LabelNames("method", "backend", "resource"),
        )
        active <- registry.gauge(
          name = s"$prefix${ MetricNames.active }",
          help = "Number of active requests",
          labels = LabelNames("method", "backend", "resource"),
        )
        success <- registry.counter(
          name = s"$prefix${ MetricNames.success }",
          help = "Number of successful requests",
          labels = LabelNames("method", "status", "backend", "resource"),
        )
        error <- registry.counter(
          name = s"$prefix${ MetricNames.error }",
          help = "Number of errored requests",
          labels = LabelNames("method", "status", "backend", "resource"),
        )
        failure <- registry.counter(
          name = s"$prefix${ MetricNames.failure }",
          help = "Number of failed requests",
          labels = LabelNames("method", "backend", "resource"),
        )
        requestSize <- registry.summary(
          name = s"$prefix${ MetricNames.requestSize }",
          help = "Request size in bytes",
          labels = LabelNames("method", "backend", "resource"),
          quantiles = Quantiles.Default,
        )
        responseSize <- registry.summary(
          name = s"$prefix${ MetricNames.responseSize }",
          help = "Response size in bytes",
          labels = LabelNames("method", "status", "backend", "resource"),
          quantiles = Quantiles.Default,
        )

        backend = SmetricsBackend(
          stubBackend,
          durationMapper = { req =>
            duration.labels(methodLabel(req), backendLabel, resourceLabel).some
          },
          activeMapper = { req =>
            active.labels(methodLabel(req), backendLabel, resourceLabel).some
          },
          successMapper = { (req, rsp) =>
            success.labels(methodLabel(req), statusLabel(rsp), backendLabel, resourceLabel).some
          },
          errorMapper = { (req, rsp) =>
            error.labels(methodLabel(req), statusLabel(rsp), backendLabel, resourceLabel).some
          },
          failureMapper = { (req, _) =>
            failure.labels(methodLabel(req), backendLabel, resourceLabel).some
          },
          requestSizeMapper = { req =>
            requestSize.labels(methodLabel(req), backendLabel, resourceLabel).some
          },
          responseSizeMapper = { (req, rsp) =>
            responseSize
              .labels(methodLabel(req), statusLabel(rsp), backendLabel, resourceLabel)
              .some
          },
        )
        _ <- Resource.eval {
          basicRequest
            .post(`/`)
            .body(body)
            .send(backend)
        }
        events <- registry.events.toResource
      } yield {
        withClue(events) {
          events.size shouldBe 6
          events.collect {
            case MetricEvent(
                  "client_http_client_request_size_bytes",
                  "summary",
                  List("POST", "labelForBackend", "labelForResource"),
                  "observe",
                  2.0,
                ) =>
              1
            case MetricEvent(
                  "client_http_client_requests_active",
                  "gauge",
                  List("POST", "labelForBackend", "labelForResource"),
                  "inc",
                  1.0,
                ) =>
              2
            case MetricEvent(
                  "client_http_client_request_duration_seconds",
                  "histogram",
                  List("POST", "labelForBackend", "labelForResource"),
                  "observe",
                  `[0, 0.1]`(_),
                ) =>
              3
            case MetricEvent(
                  "client_http_client_requests_active",
                  "gauge",
                  List("POST", "labelForBackend", "labelForResource"),
                  "dec",
                  1.0,
                ) =>
              4
            case MetricEvent(
                  "client_http_client_response_size_bytes",
                  "summary",
                  List("POST", "2xx", "labelForBackend", "labelForResource"),
                  "observe",
                  7.0,
                ) =>
              5
            case MetricEvent(
                  "client_http_client_requests_success",
                  "counter",
                  List("POST", "2xx", "labelForBackend", "labelForResource"),
                  "inc",
                  1.0,
                ) =>
              6
          } shouldBe List(1, 2, 3, 4, 5, 6)
        }
      }

      resource.use(_.pure[IO])
    }
  }
}

object SmetricsBackendSpec {
  case class MetricEvent(
    name: String,
    metricType: String,
    labels: List[String],
    op: String,
    value: Double,
  )

  class InMemoryCollectorRegistry(state: Ref[IO, Vector[MetricEvent]]) extends CollectorRegistry[IO] {

    def events: IO[Vector[MetricEvent]] = state.get

    private def record(
      name: String,
      metricType: String,
      labels: List[String],
      op: String,
      value: Double,
    ): IO[Unit] =
      state.update(events => events :+ MetricEvent(name, metricType, labels, op, value))

    override def counter[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[IO, B[Counter[IO]]] =
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
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[IO, B[Counter[IO]]] =
      counter(name, help, labels)(magnet)

    override def gauge[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[IO, B[Gauge[IO]]] =
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
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[IO, B[Gauge[IO]]] =
      gauge(name, help, labels)(magnet)

    override def histogram[A, B[_]](
      name: String,
      help: String,
      buckets: Buckets,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[IO, B[Histogram[IO]]] =
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
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[IO, B[Histogram[IO]]] =
      histogram(name, help, buckets, labels)(magnet)

    override def summary[A, B[_]](
      name: String,
      help: String,
      quantiles: Quantiles,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[IO, B[Summary[IO]]] =
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
      labels: A,
    )(implicit
      magnet: LabelsMagnetInitialized[A, B],
    ): Resource[IO, B[Summary[IO]]] =
      summary(name, help, quantiles, labels)(magnet)

    override def info[A, B[_]](
      name: String,
      help: String,
      labels: A,
    )(implicit
      magnet: LabelsMagnet[A, B],
    ): Resource[IO, B[Info[IO]]] =
      Resource.pure {
        magnet.withValues { labelValues =>
          new Info[IO] {
            override def set(): IO[Unit] = record(name, "info", labelValues, "set", 0d)
          }
        }
      }
  }

  object InMemoryCollectorRegistry {
    def make: IO[InMemoryCollectorRegistry] =
      for {
        ref <- Ref.of[IO, Vector[MetricEvent]](Vector.empty)
      } yield new InMemoryCollectorRegistry(ref)
  }

  case class Within(a: Double, b: Double) {
    def unapply(value: Double): Option[Double] =
      Option.when(value >= a && value <= b)(value)
  }

  implicit class ResponseOps[A](val response: Response[A]) extends AnyVal {
    def withContentLength(length: Long): Response[A] =
      response.copy(headers = response.headers :+ Header.contentLength(length))
  }

}
