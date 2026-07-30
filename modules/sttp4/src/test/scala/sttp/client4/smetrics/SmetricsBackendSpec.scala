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
import sttp.capabilities.Effect
import sttp.client4.*
import sttp.client4.ResponseException.{DeserializationException, UnexpectedStatusCode}
import sttp.client4.impl.cats.implicits.*
import sttp.client4.smetrics.SmetricsBackend.{DefaultBuckets, MetricNames, methodLabel, statusLabel}
import sttp.client4.smetrics.SmetricsBackendSpec.*
import sttp.client4.testing.BackendStub
import sttp.client4.testing.ResponseStub
import sttp.model.{Header, ResponseMetadata, StatusCode}

import scala.concurrent.duration.*

class SmetricsBackendSpec extends AsyncFunSuite with Matchers {

  implicit val tt: ToTry[IO] = ToTry.ioToTry

  def inMemoryCollectorRegistry: CollectorRegistry[IO] = CollectorRegistry.empty[IO]

  private val `(0, 0.1]` = Within(0, 0.1)

  def collect[A](
    stub: BackendStub[IO] => BackendStub[IO],
    send: Backend[IO] => IO[A],
  ): IO[Vector[MetricEvent]] = {
    for {
      registry <- InMemoryCollectorRegistry.make
      backendAllocated <- SmetricsBackend
        .default1(
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
        case MetricEvent("http_client_request_duration_seconds", "histogram", List("POST"), "observe", `(0, 0.1]`(_)) =>
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
                `(0, 0.1]`(_),
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
        case MetricEvent("http_client_request_duration_seconds", "histogram", List("POST"), "observe", `(0, 0.1]`(_)) =>
          3
        case MetricEvent("http_client_requests_active", "gauge", List("POST"), "dec", 1.0) => 4
        case MetricEvent("http_client_requests_failure", "counter", List("POST"), "inc", 1.0) => 5
      } shouldBe List(1, 2, 3, 4, 5)
    }.run()
  }

  test("deserialization failure after body received records metrics exactly once") {
    collect(
      stub =>
        stub.whenAnyRequest
          .thenRespond(
            ResponseStub.adjust(
              body = html,
              code = StatusCode.Ok,
            ).withContentLength(html.length.toLong),
          ),
      backend =>
        basicRequest
          .post(`/`)
          .body(body)
          .response {
            asString.map[Either[String, String]] { _ =>
              throw DeserializationException(
                "Unknown body",
                new Exception("Unable to parse"),
                ResponseMetadata(StatusCode.Ok, "OK", Nil),
              )
            }
          }
          .send(backend)
          .attempt,
    ).map { events =>
      withClue(events) {
        events.size shouldBe 6
        events.count(event => event.name == MetricNames.active && event.op == "dec") shouldBe 1
        events.count(event => event.name == MetricNames.duration && event.op == "observe") shouldBe 1
        // counters reflect the HTTP-level outcome: the response itself was a 2xx, so the body
        // handling failure is counted as a success, not as an error or failure
        events.collect {
          case MetricEvent(MetricNames.success, "counter", List("POST", "2xx"), "inc", 1.0) => ()
        }.size shouldBe 1
      }
    }.run()
  }

  test("response exception without received body still records metrics") {
    collect(
      stub =>
        stub.whenAnyRequest
          .thenThrow(
            UnexpectedStatusCode("not found", ResponseMetadata(StatusCode.NotFound, "Not Found", Nil)),
          ),
      backend => basicRequest.post(`/`).body(body).send(backend).attempt,
    ).map { events =>
      withClue(events) {
        events.count(event => event.name == MetricNames.active && event.op == "dec") shouldBe 1
        events.count(event => event.name == MetricNames.duration && event.op == "observe") shouldBe 1
        events.collect {
          case MetricEvent(MetricNames.error, "counter", List("POST", "4xx"), "inc", 1.0) => ()
        }.size shouldBe 1
      }
    }.run()
  }

  test("streaming response with body never received still decrements the active gauge") {
    runIO {
      // Models a real backend serving an `asStreamUnsafe(...)` response: `send` completes when the
      // headers arrive, and the `onBodyReceived` callback fires only if the caller fully consumes
      // the stream - which here never happens.
      val bodyNeverReceivedBackend: Backend[IO] = new Backend[IO] {
        override val monad: sttp.monad.MonadError[IO] = sttp.monad.MonadError[IO]
        override def send[T](request: GenericRequest[T, Any with Effect[IO]]): IO[Response[T]] =
          IO.pure(ResponseStub.exact("streamed body, never consumed").asInstanceOf[Response[T]])
        override def close(): IO[Unit] = IO.unit
      }

      for {
        registry <- InMemoryCollectorRegistry.make
        backendAllocated <- SmetricsBackend.default1(bodyNeverReceivedBackend, registry).allocated
        (backend, release) = backendAllocated
        _ <- basicRequest.get(`/`).send(backend)
        events <- registry.events
        _ <- release
      } yield withClue(events) {
        events.count(event => event.name == MetricNames.active && event.op == "dec") shouldBe 1
        events.count(event => event.name == MetricNames.duration && event.op == "observe") shouldBe 1
      }
    }
  }

  test("duration histogram can be labelled by response status") {
    runIO {
      val stubBackend = BackendStub[IO](sttp.monad.MonadError[IO]).whenAnyRequest.thenRespondNotFound()

      val resource = for {
        registry <- InMemoryCollectorRegistry.make.toResource
        duration <- registry.histogram(
          name = MetricNames.duration,
          help = "Request duration in seconds",
          buckets = Buckets(NonEmptyList.fromListUnsafe(DefaultBuckets)),
          labels = LabelNames("method", "status"),
        )
        backend = SmetricsBackend(
          stubBackend,
          durationMapper = { (req, outcome) =>
            duration.labels(methodLabel(req), outcome.fold(_ => "failure", statusLabel)).some
          },
          activeMapper = { _ => Option.empty[Gauge[IO]] },
          successMapper = { (_, _) => Option.empty[Counter[IO]] },
          errorMapper = { (_, _) => Option.empty[Counter[IO]] },
          failureMapper = { (_, _) => Option.empty[Counter[IO]] },
          requestSizeMapper = { _ => Option.empty[Summary[IO]] },
          responseSizeMapper = { (_, _) => Option.empty[Summary[IO]] },
        )
        _ <- basicRequest.get(`/`).send(backend).toResource
        events <- registry.events.toResource
      } yield withClue(events) {
        events.collect {
          case MetricEvent(MetricNames.duration, "histogram", List("GET", "4xx"), "observe", _) => ()
        }.size shouldBe 1
      }

      resource.use(_.pure[IO])
    }
  }

  test("gauge decrement is not lost when duration recording fails") {
    runIO {
      val stubBackend = BackendStub[IO](sttp.monad.MonadError[IO]).whenAnyRequest.thenRespondOk()

      val failingDuration: Histogram[IO] = new Histogram[IO] {
        override def observe(value: Double): IO[Unit] =
          IO.raiseError(new RuntimeException("metrics store unavailable"))
      }

      val resource = for {
        registry <- InMemoryCollectorRegistry.make.toResource
        active <- registry.gauge(
          name = MetricNames.active,
          help = "Number of active requests",
          labels = LabelNames("method"),
        )
        backend = SmetricsBackend(
          stubBackend,
          durationMapper = { (_, _) => failingDuration.some },
          activeMapper = { req => active.labels(methodLabel(req)).some },
          successMapper = { (_, _) => Option.empty[Counter[IO]] },
          errorMapper = { (_, _) => Option.empty[Counter[IO]] },
          failureMapper = { (_, _) => Option.empty[Counter[IO]] },
          requestSizeMapper = { _ => Option.empty[Summary[IO]] },
          responseSizeMapper = { (_, _) => Option.empty[Summary[IO]] },
        )
        _ <- basicRequest.get(`/`).send(backend).toResource
        events <- registry.events.toResource
      } yield withClue(events) {
        events.count(event => event.name == MetricNames.active && event.op == "dec") shouldBe 1
      }

      resource.use(_.pure[IO])
    }
  }

  test("mappers returning None disable all metrics") {
    runIO {
      val stubBackend = BackendStub[IO](sttp.monad.MonadError[IO]).whenAnyRequest.thenRespondOk()

      for {
        registry <- InMemoryCollectorRegistry.make
        backend = SmetricsBackend(
          stubBackend,
          durationMapper = { (_, _) => Option.empty[Histogram[IO]] },
          activeMapper = { _ => Option.empty[Gauge[IO]] },
          successMapper = { (_, _) => Option.empty[Counter[IO]] },
          errorMapper = { (_, _) => Option.empty[Counter[IO]] },
          failureMapper = { (_, _) => Option.empty[Counter[IO]] },
          requestSizeMapper = { _ => Option.empty[Summary[IO]] },
          responseSizeMapper = { (_, _) => Option.empty[Summary[IO]] },
        )
        response <- basicRequest.post(`/`).body(body).send(backend)
        events <- registry.events
      } yield {
        response.code shouldBe StatusCode.Ok
        events shouldBe empty
      }
    }
  }

  test("active gauge reflects in-flight requests under concurrency") {
    val requestsNumber = 5

    def activeOps(events: Vector[MetricEvent], op: String): Int =
      events.count(event => event.name == MetricNames.active && event.op == op)

    def awaitInFlight(registry: InMemoryCollectorRegistry, expected: Int): IO[Vector[MetricEvent]] =
      registry.events.flatMap { events =>
        if (activeOps(events, "inc") >= expected) IO.pure(events)
        else IO.sleep(10.millis) >> awaitInFlight(registry, expected)
      }

    runIO {
      for {
        gate <- Deferred[IO, Unit]
        registry <- InMemoryCollectorRegistry.make
        stubBackend = BackendStub[IO](sttp.monad.MonadError[IO]).whenAnyRequest
          .thenRespondF(gate.get.as(ResponseStub.adjust("", StatusCode.Ok)))
        backendAllocated <- SmetricsBackend.default1(stubBackend, registry).allocated
        (backend, release) = backendAllocated
        fibers <- basicRequest.get(`/`).send(backend).start.replicateA(requestsNumber)
        inFlightEvents <- awaitInFlight(registry, requestsNumber)
        _ = withClue(inFlightEvents) {
          activeOps(inFlightEvents, "inc") shouldBe requestsNumber
          activeOps(inFlightEvents, "dec") shouldBe 0
        }
        _ <- gate.complete(())
        _ <- fibers.traverse_(_.join)
        finalEvents <- registry.events
        _ <- release
      } yield withClue(finalEvents) {
        activeOps(finalEvents, "inc") shouldBe requestsNumber
        activeOps(finalEvents, "dec") shouldBe requestsNumber
      }
    }
  }

  test("cancelled request decrements the active gauge and counts a failure") {
    def awaitActiveInc(registry: InMemoryCollectorRegistry): IO[Unit] =
      registry.events.flatMap { events =>
        if (events.exists(event => event.name == MetricNames.active && event.op == "inc")) IO.unit
        else IO.sleep(10.millis) >> awaitActiveInc(registry)
      }

    runIO {
      for {
        gate <- Deferred[IO, Unit]
        registry <- InMemoryCollectorRegistry.make
        stubBackend = BackendStub[IO](sttp.monad.MonadError[IO]).whenAnyRequest
          .thenRespondF(gate.get.as(ResponseStub.adjust("", StatusCode.Ok)))
        backendAllocated <- SmetricsBackend.default1(stubBackend, registry).allocated
        (backend, release) = backendAllocated
        fiber <- basicRequest.get(`/`).send(backend).start
        _ <- awaitActiveInc(registry)
        _ <- fiber.cancel
        events <- registry.events
        _ <- release
      } yield withClue(events) {
        events.size shouldBe 4
        events.count(event => event.name == MetricNames.active && event.op == "inc") shouldBe 1
        events.count(event => event.name == MetricNames.active && event.op == "dec") shouldBe 1
        events.count(event => event.name == MetricNames.duration && event.op == "observe") shouldBe 1
        events.collect {
          case MetricEvent(MetricNames.failure, "counter", List("GET"), "inc", 1.0) => ()
        }.size shouldBe 1
      }
    }
  }

  test("configure prefix") {
    runIO {
      val stubBackend = BackendStub[IO](sttp.monad.MonadError[IO]).whenAnyRequest.thenRespondOk()

      for {
        registry <- InMemoryCollectorRegistry.make
        backendAllocated <- SmetricsBackend
          .default1(
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
          durationMapper = { (req, _) =>
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
                  `(0, 0.1]`(_),
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
      Option.when(value > a && value <= b)(value)
  }

  implicit class ResponseOps[A](val response: Response[A]) extends AnyVal {
    def withContentLength(length: Long): Response[A] =
      response.copy(headers = response.headers :+ Header.contentLength(length))
  }

}
