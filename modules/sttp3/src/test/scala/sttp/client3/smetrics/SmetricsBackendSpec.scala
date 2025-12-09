package sttp.client3.smetrics

import cats.data.NonEmptyList
import cats.effect.Ref
import cats.effect._
import cats.syntax.all._
import com.evolutiongaming.smetrics.IOSuite._
import com.evolutiongaming.smetrics._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.client3.impl.cats.implicits._
import sttp.client3.smetrics.SmetricsBackend.{DefaultBuckets, MetricNames, methodLabel, statusLabel}
import sttp.client3.smetrics.SmetricsBackendSpec._
import sttp.client3.testing.SttpBackendStub
import sttp.model.{Header, StatusCode}

class SmetricsBackendSpec extends AsyncFunSuite with Matchers {

  def inMemoryCollectorRegistry: CollectorRegistry[IO] = CollectorRegistry.empty[IO]

  private val `(0, 0.1]` = Within(0.00001, 0.1)

  def collect[A](
    stub: SttpBackendStub[IO, Any] => SttpBackendStub[IO, Any],
    send: SttpBackend[IO, Any] => IO[A],
  ): IO[Vector[MetricEvent]] = {
    for {
      registry <- InMemoryCollectorRegistry.make
      backendAllocated <- SmetricsBackend
        .default(
          stub(SttpBackendStub[IO, Any](sttp.monad.MonadError[IO])),
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
            Response(
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
        case MetricEvent("sttp_request_size_bytes", "summary", List("POST"), "observe", `reqSize`) => 1
        case MetricEvent("sttp_requests_in_progress", "gauge", List("POST"), "inc", 1.0) => 2
        case MetricEvent("sttp_request_latency_seconds", "histogram", List("POST"), "observe", `(0, 0.1]`(_)) => 3
        case MetricEvent("sttp_requests_in_progress", "gauge", List("POST"), "dec", 1.0) => 4
        case MetricEvent("sttp_response_size_bytes", "summary", List("POST", "2xx"), "observe", `rspSize`) => 5
        case MetricEvent("sttp_requests_success_count", "counter", List("POST", "2xx"), "inc", 1.0) => 6
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
              Response(
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
          case MetricEvent("sttp_request_size_bytes", "summary", List("POST"), "observe", `reqSize`) => 1
          case MetricEvent("sttp_requests_in_progress", "gauge", List("POST"), "inc", 1.0) => 2
          case MetricEvent("sttp_request_latency_seconds", "histogram", List("POST"), "observe", `(0, 0.1]`(_)) => 3
          case MetricEvent("sttp_requests_in_progress", "gauge", List("POST"), "dec", 1.0) => 4
          case MetricEvent("sttp_response_size_bytes", "summary", List("POST", `sts`), "observe", `rspSize`) => 5
          case MetricEvent("sttp_requests_error_count", "counter", List("POST", `sts`), "inc", 1.0) => 6
        } shouldBe List(1, 2, 3, 4, 5, 6)
      }
    }

    { check(StatusCode.NotFound) *> check(StatusCode.InternalServerError) }.run()
  }

  test("failure request") {
    collect(
      stub => stub.whenAnyRequest.thenRespondOk(),
      backend =>
        basicRequest
          .post(`/`)
          .body(body)
          .response {
            asString.map[Either[String, String]] { _ =>
              throw DeserializationException("Unknown body", new Exception("Unable to parse"))
            }
          }
          .send(backend)
          .attempt
          .map { errorOrResponse =>
            assertThrows[SttpClientException](errorOrResponse.toTry.get)
          },
    ).map { events =>
      val `body.length` = body.length.toDouble

      events.size shouldBe 5
      events.collect {
        case MetricEvent("sttp_request_size_bytes", "summary", List("POST"), "observe", `body.length`) => 1
        case MetricEvent("sttp_requests_in_progress", "gauge", List("POST"), "inc", 1.0) => 2
        case MetricEvent("sttp_request_latency_seconds", "histogram", List("POST"), "observe", `(0, 0.1]`(_)) => 3
        case MetricEvent("sttp_requests_in_progress", "gauge", List("POST"), "dec", 1.0) => 4
        case MetricEvent("sttp_requests_failure_count", "counter", List("POST"), "inc", 1.0) => 5
      } shouldBe List(1, 2, 3, 4, 5)
    }.run()
  }

  test("configure prefix") {
    runIO {
      val stubBackend = SttpBackendStub[IO, Any](sttp.monad.MonadError[IO]).whenAnyRequest.thenRespondOk()

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
      val stubBackend = SttpBackendStub[IO, Any](sttp.monad.MonadError[IO]).whenAnyRequest.thenRespond(
        Response(
          body = html,
          code = StatusCode.Ok,
        ).withContentLength(html.length.toLong),
      )

      def label(name: String)(req: Request[_, _]): String =
        req.tag(name).map(_.toString).getOrElse("unknown")

      val backendLabel = label("backend")(_)
      val resourceLabel = label("resource")(_)

      val prefix = "client_"
      val resource = for {
        registry <- InMemoryCollectorRegistry.make.toResource
        latency <- registry.histogram(
          name = s"$prefix${ MetricNames.latency }",
          help = "Request latency in seconds",
          buckets = Buckets(NonEmptyList.fromListUnsafe(DefaultBuckets)),
          labels = LabelNames("method", "backend", "resource"),
        )
        inProgress <- registry.gauge(
          name = s"$prefix${ MetricNames.inProgress }",
          help = "Number of requests in progress",
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
          latencyMapper = { req =>
            latency.labels(methodLabel(req), backendLabel(req), resourceLabel(req)).some
          },
          inProgressMapper = { req =>
            inProgress.labels(methodLabel(req), backendLabel(req), resourceLabel(req)).some
          },
          successMapper = { (req, rsp) =>
            success.labels(methodLabel(req), statusLabel(rsp), backendLabel(req), resourceLabel(req)).some
          },
          errorMapper = { (req, rsp) =>
            error.labels(methodLabel(req), statusLabel(rsp), backendLabel(req), resourceLabel(req)).some
          },
          failureMapper = { (req, _) =>
            failure.labels(methodLabel(req), backendLabel(req), resourceLabel(req)).some
          },
          requestSizeMapper = { req =>
            requestSize.labels(methodLabel(req), backendLabel(req), resourceLabel(req)).some
          },
          responseSizeMapper = { (req, rsp) =>
            responseSize
              .labels(methodLabel(req), statusLabel(rsp), backendLabel(req), resourceLabel(req))
              .some
          },
        )
        _ <- Resource.eval {
          basicRequest
            .post(`/`)
            .body(body)
            .tag("backend", "primary")
            .tag("resource", "users")
            .send(backend)
        }
        events <- registry.events.toResource
      } yield {
        withClue(events) {
          events.size shouldBe 6
          events.collect {
            case MetricEvent(
                  "client_sttp_request_size_bytes",
                  "summary",
                  List("POST", "primary", "users"),
                  "observe",
                  2.0,
                ) =>
              1
            case MetricEvent(
                  "client_sttp_requests_in_progress",
                  "gauge",
                  List("POST", "primary", "users"),
                  "inc",
                  1.0,
                ) =>
              2
            case MetricEvent(
                  "client_sttp_request_latency_seconds",
                  "histogram",
                  List("POST", "primary", "users"),
                  "observe",
                  `(0, 0.1]`(_),
                ) =>
              3
            case MetricEvent(
                  "client_sttp_requests_in_progress",
                  "gauge",
                  List("POST", "primary", "users"),
                  "dec",
                  1.0,
                ) =>
              4
            case MetricEvent(
                  "client_sttp_response_size_bytes",
                  "summary",
                  List("POST", "2xx", "primary", "users"),
                  "observe",
                  7.0,
                ) =>
              5
            case MetricEvent(
                  "client_sttp_requests_success_count",
                  "counter",
                  List("POST", "2xx", "primary", "users"),
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
      Option.when(value > a && value <= b)(value)
  }

  implicit class ResponseOps[A](val response: Response[A]) extends AnyVal {
    def withContentLength(length: Long): Response[A] =
      response.copy(headers = response.headers :+ Header.contentLength(length))
  }

}
