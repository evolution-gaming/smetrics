package sttp.client3.smetrics

import cats._
import cats.data.NonEmptyList
import cats.effect.Resource
import cats.syntax.all._
import cats.effect.kernel.Clock
import com.evolutiongaming.catshelper.MeasureDuration
import sttp.client3._

import sttp.client3.listener._
import com.evolutiongaming.smetrics._

/** Factory for creating STTP backends that record metrics using smetrics.
  *
  * This backend wraps an existing STTP backend and records various metrics about HTTP requests:
  *   - '''Request latency''' (histogram): Records the time taken for each request in seconds
  *   - '''Requests in progress''' (gauge): Tracks the number of currently in-flight requests
  *   - '''Success/error/failure counts''' (counters): Counts successful (2xx), error (4xx/5xx), and failed (exception)
  *     requests
  *   - '''Request and response sizes''' (summaries): Records the size of request and response bodies in bytes
  *
  * ==Usage==
  *
  * The simplest way to use this backend is with a `CollectorRegistry`:
  *
  * {{{
  * import cats.effect.IO
  * import sttp.client3._
  * import sttp.client3.smetrics.SmetricsBackend
  * import com.evolutiongaming.smetrics.CollectorRegistry
  *
  * val backend: SttpBackend[IO, Any] = ???
  * val registry: CollectorRegistry[IO] = ???
  *
  * SmetricsBackend(backend, registry).use { backend => ??? }
  * }}}
  *
  * ==Custom Metric Prefixes==
  *
  * You can customize the metric name prefix:
  *
  * {{{
  * SmetricsBackend(backend, registry, prefix = "custom_")
  * }}}
  *
  * This will generate metrics like:
  *   - `custom_request_latency_seconds`
  *   - `custom_requests_in_progress`
  *   - `custom_requests_success_count`
  *   - etc.
  *
  * ==Custom Metric Mappers==
  *
  * For advanced use cases, you can provide custom mappers to control exactly which metrics are recorded for each
  * request:
  *
  * {{{
  * import com.evolutiongaming.smetrics._
  *
  * val backend = SmetricsBackend(
  *   delegate = underlyingBackend,
  *   latencyMapper = { req => ??? },
  *   inProgressMapper = ???,
  *   successMapper = ???,
  *   errorMapper = ???,
  *   failureMapper = ???,
  *   requestSizeMapper = ???,
  *   responseSizeMapper = ???
  * )
  * }}}
  *
  * ==Labels==
  *
  * By default, the following labels are attached to metrics:
  *   - '''method''': HTTP method (GET, POST, etc.)
  *   - '''status''': Response status category (1xx, 2xx, 3xx, 4xx, 5xx)
  *
  * ==Histogram Buckets==
  *
  * The default latency histogram buckets cover common response times from 5ms to 10 seconds:
  * {{{
  * .005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10
  * }}}
  *
  * ==Thread Safety==
  *
  * This backend is thread-safe and can be shared across multiple concurrent requests.
  *
  * @see
  *   [[https://github.com/evolution-gaming/smetrics smetrics]]
  * @see
  *   [[https://sttp.softwaremill.com/en/stable/ STTP documentation]]
  */
object SmetricsBackend {

  /** Default metric names used by SmetricsBackend.
    */
  object MetricNames {

    /** Default prefix for all metrics */
    val DefaultPrefix = "sttp_"

    /** Metric name for request latency histogram */
    def latency(prefix: String = DefaultPrefix): String = s"${prefix}request_latency_seconds"

    /** Metric name for requests in progress gauge */
    def inProgress(prefix: String = DefaultPrefix): String = s"${prefix}requests_in_progress"

    /** Metric name for successful requests counter */
    def success(prefix: String = DefaultPrefix): String = s"${prefix}requests_success_count"

    /** Metric name for errored requests counter */
    def error(prefix: String = DefaultPrefix): String = s"${prefix}requests_error_count"

    /** Metric name for failed requests counter */
    def failure(prefix: String = DefaultPrefix): String = s"${prefix}requests_failure_count"

    /** Metric name for request size summary */
    def requestSize(prefix: String = DefaultPrefix): String = s"${prefix}request_size_bytes"

    /** Metric name for response size summary */
    def responseSize(prefix: String = DefaultPrefix): String = s"${prefix}response_size_bytes"
  }

  /** Default histogram buckets for latency measurements in seconds. Covers common response times from 5ms to 10s.
    */
  val DefaultBuckets: List[Double] = List(.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10)

  /** Returns the HTTP method label for a request, used for metric labeling.
    *
    * @param req
    *   The STTP request
    * @return
    *   The HTTP method in uppercase (e.g., "GET", "POST")
    */
  def methodLabel(req: Request[_, _]): String = req.method.method.toUpperCase

  /** Returns the status label for a response, used for metric labeling.
    *
    * Maps the HTTP status code to a category string:
    *   - "1xx" for informational responses
    *   - "2xx" for successful responses
    *   - "3xx" for redirects
    *   - "4xx" for client errors
    *   - "5xx" for server errors
    *   - Otherwise, returns the numeric status code as a string
    *
    * @param rsp
    *   The STTP response
    * @return
    *   The status label string (e.g., "2xx", "404")
    */
  def statusLabel(rsp: Response[_]): String = {
    val code = rsp.code
    if (code.isInformational) "1xx"
    else if (code.isSuccess) "2xx"
    else if (code.isRedirect) "3xx"
    else if (code.isClientError) "4xx"
    else if (code.isServerError) "5xx"
    else code.code.toString
  }

  /** Creates an STTP backend with custom metric mappers.
    *
    * This variant allows you to provide custom logic for mapping requests to specific metric instances, giving you full
    * control over metric collection and labeling.
    *
    * @param delegate
    *   The underlying STTP backend to wrap
    * @param latencyMapper
    *   Function to map a request to a histogram for recording latency
    * @param inProgressMapper
    *   Function to map a request to a gauge for tracking in-progress requests
    * @param successMapper
    *   Function to map a request and response to a counter for successful requests
    * @param errorMapper
    *   Function to map a request and response to a counter for errored requests
    * @param failureMapper
    *   Function to map a request and exception to a counter for failed requests
    * @param requestSizeMapper
    *   Function to map a request to a summary for request sizes
    * @param responseSizeMapper
    *   Function to map a request and response to a summary for response sizes
    * @tparam F
    *   The effect type (e.g., IO, Task)
    * @tparam P
    *   The capabilities type for the backend
    * @return
    *   A new backend that records metrics according to the provided mappers
    */
  def apply[F[_]: Clock: Monad, P](
      delegate: SttpBackend[F, P],
      latencyMapper: Request[_, _] => Option[Histogram[F]],
      inProgressMapper: Request[_, _] => Option[Gauge[F]],
      successMapper: (Request[_, _], Response[_]) => Option[Counter[F]],
      errorMapper: (Request[_, _], Response[_]) => Option[Counter[F]],
      failureMapper: (Request[_, _], Throwable) => Option[Counter[F]],
      requestSizeMapper: Request[_, _] => Option[Summary[F]],
      responseSizeMapper: (Request[_, _], Response[_]) => Option[Summary[F]],
  ): SttpBackend[F, P] = {
    // redirects should be handled before prometheus
    new FollowRedirectsBackend[F, P](
      new ListenerBackend[F, P, State[F]](
        delegate,
        new PrometheusListener[F](
          latencyMapper: Request[_, _] => Option[Histogram[F]],
          inProgressMapper: Request[_, _] => Option[Gauge[F]],
          successMapper: (Request[_, _], Response[_]) => Option[Counter[F]],
          errorMapper: (Request[_, _], Response[_]) => Option[Counter[F]],
          failureMapper: (Request[_, _], Throwable) => Option[Counter[F]],
          requestSizeMapper: Request[_, _] => Option[Summary[F]],
          responseSizeMapper: (Request[_, _], Response[_]) => Option[Summary[F]],
        ),
      )
    )
  }

  /** Creates an STTP backend with automatic metric collection using a CollectorRegistry.
    *
    * This is the recommended way to create a metrics-enabled backend. It automatically sets up all standard metrics
    * with sensible defaults:
    *   - Request latency histogram with method label
    *   - In-progress requests gauge with method label
    *   - Success/error counters with method and status labels
    *   - Failure counter with method label
    *   - Request/response size summaries with appropriate labels
    *
    * The backend returns a Resource that properly manages the lifecycle of the metrics.
    *
    * Example usage:
    * {{{
    * import cats.effect.IO
    * import sttp.client3._
    * import sttp.client3.smetrics.SmetricsBackend
    * import com.evolutiongaming.smetrics.CollectorRegistry
    *
    * val backend: SttpBackend[IO, Any] = ???
    * val registry: CollectorRegistry[IO] = ???
    *
    * SmetricsBackend(backend, registry, prefix = "myapp_").use { metricsBackend =>
    *   basicRequest
    *     .get(uri"https://api.example.com/users")
    *     .send(metricsBackend)
    * }
    * }}}
    *
    * @param delegate
    *   The underlying STTP backend to wrap
    * @param collectorRegistry
    *   The smetrics collector registry to register metrics with
    * @param prefix
    *   The metric name prefix (default: "sttp_")
    * @tparam F
    *   The effect type (e.g., IO, Task)
    * @tparam P
    *   The capabilities type for the backend
    * @return
    *   A Resource that manages the metrics-enabled backend lifecycle
    */
  def apply[F[_]: Clock: Monad, P](
      delegate: SttpBackend[F, P],
      collectorRegistry: CollectorRegistry[F],
      prefix: String = MetricNames.DefaultPrefix,
  ): Resource[F, SttpBackend[F, P]] = {
    for {
      latency      <- collectorRegistry.histogram(
                        name = MetricNames.latency(prefix),
                        help = "Request latency in seconds",
                        buckets = Buckets(NonEmptyList.fromListUnsafe(DefaultBuckets)),
                        labels = LabelNames("method")
                      )
      inProgress   <- collectorRegistry.gauge(
                        name = MetricNames.inProgress(prefix),
                        help = "Number of requests in progress",
                        labels = LabelNames("method")
                      )
      success      <- collectorRegistry.counter(
                        name = MetricNames.success(prefix),
                        help = "Number of successful requests",
                        labels = LabelNames("method", "status")
                      )
      error        <- collectorRegistry.counter(
                        name = MetricNames.error(prefix),
                        help = "Number of errored requests",
                        labels = LabelNames("method", "status")
                      )
      failure      <- collectorRegistry.counter(
                        name = MetricNames.failure(prefix),
                        help = "Number of failed requests",
                        labels = LabelNames("method")
                      )
      requestSize  <- collectorRegistry.summary(
                        name = MetricNames.requestSize(prefix),
                        help = "Request size in bytes",
                        labels = LabelNames("method"),
                        quantiles = Quantiles.Default
                      )
      responseSize <- collectorRegistry.summary(
                        name = MetricNames.responseSize(prefix),
                        help = "Response size in bytes",
                        labels = LabelNames("method", "status"),
                        quantiles = Quantiles.Default
                      )
    } yield {
      // redirects should be handled before prometheus
      new FollowRedirectsBackend[F, P](
        new ListenerBackend[F, P, State[F]](
          delegate,
          new PrometheusListener[F](
            latencyMapper = { req => latency.labels(methodLabel(req)).some },
            inProgressMapper = { req => inProgress.labels(methodLabel(req)).some },
            successMapper = { (req, rsp) => success.labels(methodLabel(req), statusLabel(rsp)).some },
            errorMapper = { (req, rsp) => error.labels(methodLabel(req), statusLabel(rsp)).some },
            failureMapper = { (req, _) => failure.labels(methodLabel(req)).some },
            requestSizeMapper = { req => requestSize.labels(methodLabel(req)).some },
            responseSizeMapper = { (req, rsp) => responseSize.labels(methodLabel(req), statusLabel(rsp)).some },
          ),
        )
      )
    }
  }

  /** Internal state passed between request lifecycle hooks.
    *
    * @param recordLatency
    *   Effect to record the request latency measurement
    * @param decInProgress
    *   Effect to decrement the in-progress requests gauge
    * @tparam F
    *   The effect type
    */
  private[this] final case class State[F[_]](recordLatency: F[Unit], decInProgress: F[Unit])

  /** Internal RequestListener implementation that records metrics for HTTP requests.
    *
    * This listener hooks into the STTP request lifecycle to:
    *   - Start latency measurement and increment in-progress gauge before the request
    *   - Record latency, decrement in-progress gauge, and update counters after the request
    *   - Handle both successful responses and exceptions
    *
    * @param latencyMapper
    *   Function to map a request to a histogram for recording latency
    * @param inProgressMapper
    *   Function to map a request to a gauge for tracking in-progress requests
    * @param successMapper
    *   Function to map a request and response to a counter for successful requests
    * @param errorMapper
    *   Function to map a request and response to a counter for errored requests
    * @param failureMapper
    *   Function to map a request and exception to a counter for failed requests
    * @param requestSizeMapper
    *   Function to map a request to a summary for request sizes
    * @param responseSizeMapper
    *   Function to map a request and response to a summary for response sizes
    * @tparam F
    *   The effect type
    */
  private[this] class PrometheusListener[F[_]: Clock: Monad](
      latencyMapper: Request[_, _] => Option[Histogram[F]],
      inProgressMapper: Request[_, _] => Option[Gauge[F]],
      successMapper: (Request[_, _], Response[_]) => Option[Counter[F]],
      errorMapper: (Request[_, _], Response[_]) => Option[Counter[F]],
      failureMapper: (Request[_, _], Throwable) => Option[Counter[F]],
      requestSizeMapper: Request[_, _] => Option[Summary[F]],
      responseSizeMapper: (Request[_, _], Response[_]) => Option[Summary[F]],
  ) extends RequestListener[F, State[F]] {

    /** Called before a request is sent.
      *
      * This method:
      *   - Starts latency measurement if a latency histogram is configured
      *   - Increments the in-progress gauge if configured
      *   - Records request size if a summary is configured
      *
      * Returns State containing effects to record latency and decrement in-progress gauge.
      *
      * @param request
      *   The HTTP request about to be sent
      * @return
      *   State containing cleanup effects to run after the request completes
      */
    override def beforeRequest(request: Request[_, _]): F[State[F]] = {
      val latency = for {
        latency <- latencyMapper(request)
      } yield for {
        duration <- MeasureDuration[F].start
      } yield duration.flatMap { duration => latency.observe(duration.toUnit(scala.concurrent.duration.SECONDS)) }

      val inProgress = inProgressMapper(request)

      val requestSize = for {
        requestSize <- requestSizeMapper(request)
        size        <- request.contentLength.map(_.toDouble)
      } yield requestSize.observe(size)

      val unit = Applicative[F].unit

      for {
        recordLatency <- latency.getOrElse(unit.pure[F])
        _             <- requestSize.getOrElse(unit)
        _             <- inProgress.map(_.inc()).getOrElse(unit)
      } yield State(
        recordLatency = recordLatency,
        decInProgress = inProgress.map(_.dec()).getOrElse(unit)
      )
    }

    /** Called when a request throws an exception.
      *
      * This method:
      *   - Handles HttpError exceptions by treating them as successful responses with error status codes
      *   - Records latency measurement
      *   - Decrements in-progress gauge
      *   - Increments failure counter for non-HTTP exceptions
      *
      * @param request
      *   The HTTP request that failed
      * @param state
      *   State containing cleanup effects from beforeRequest
      * @param e
      *   The exception that was thrown
      * @return
      *   Effect completing the metric recording
      */
    override def requestException(
        request: Request[_, _],
        state: State[F],
        e: Exception
    ): F[Unit] = {
      HttpError.find(e) match {
        case Some(HttpError(body, statusCode)) =>
          requestSuccessful(request, Response(body, statusCode).copy(request = request.onlyMetadata), state)
        case _                                 =>
          for {
            _ <- state.recordLatency
            _ <- state.decInProgress
            _ <- failureMapper(request, e).map(_.inc()).sequence
          } yield ()
      }
    }

    /** Called when a request completes successfully.
      *
      * This method:
      *   - Records latency measurement
      *   - Decrements in-progress gauge
      *   - Records response size if configured
      *   - Increments success counter for 2xx responses, error counter otherwise
      *
      * @param request
      *   The HTTP request that was sent
      * @param response
      *   The HTTP response that was received
      * @param state
      *   State containing cleanup effects from beforeRequest
      * @return
      *   Effect completing the metric recording
      */
    override def requestSuccessful(
        request: Request[_, _],
        response: Response[_],
        state: State[F]
    ): F[Unit] = {
      for {
        _      <- state.recordLatency
        _      <- state.decInProgress
        _      <- {
                    for {
                      responseSize <- responseSizeMapper(request, response)
                      size         <- response.contentLength.map(_.toDouble)
                    } yield responseSize.observe(size)
                  }.sequence
        counter = if (response.isSuccess)
                    successMapper
                  else
                    errorMapper
        _      <- counter(request, response).map(_.inc()).sequence
      } yield ()
    }

  }
}
