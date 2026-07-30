package sttp.client3.smetrics

import cats.*
import cats.data.NonEmptyList
import cats.effect.{Clock, Resource}
import cats.syntax.all.*
import com.evolutiongaming.catshelper.MeasureDuration
import com.evolutiongaming.smetrics.*
import sttp.client3.*
import sttp.client3.listener.*

import scala.concurrent.duration.{FiniteDuration, SECONDS}

/**
 * Factory for creating STTP backends that record metrics using smetrics.
 *
 * This backend wraps an existing STTP backend and records various metrics about HTTP requests:
 *   - '''Request latency''' (histogram): Records the time taken for each request in seconds
 *   - '''Requests in progress''' (gauge): Tracks the number of currently in-flight requests
 *   - '''Success/error/failure counts''' (counters): Counts successful (2xx), error (4xx/5xx), and
 *     failed (exception) requests
 *   - '''Request and response sizes''' (summaries): Records the size of request and response bodies
 *     in bytes
 *
 * ==Usage==
 *
 * The simplest way to use this backend is with a `CollectorRegistry`:
 *
 * {{{
 * import cats.effect.IO
 * import sttp.client3.*
 * import sttp.client3.smetrics.SmetricsBackend
 * import com.evolutiongaming.smetrics.CollectorRegistry
 *
 * val backend: SttpBackend[IO, Any] = ???
 * val registry: CollectorRegistry[IO] = ???
 *
 * SmetricsBackend.default1(backend, registry).use { backend => ??? }
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
 * For advanced use cases, you can provide custom mappers to control exactly which metrics are
 * recorded for each request:
 *
 * {{{
 * import com.evolutiongaming.smetrics.*
 *
 * val backend = SmetricsBackend(
 *   delegate = underlyingBackend,
 *   latencyMapper = { (req, outcome) => ??? },
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

  /**
   * Default metric names used by SmetricsBackend.
   */
  object MetricNames {

    /**
     * Metric name for request latency histogram
     */
    val latency: String = "sttp_request_latency_seconds"

    /**
     * Metric name for requests in progress gauge
     */
    val inProgress: String = "sttp_requests_in_progress"

    /**
     * Metric name for successful requests counter
     */
    val success: String = "sttp_requests_success_count"

    /**
     * Metric name for errored requests counter
     */
    val error: String = "sttp_requests_error_count"

    /**
     * Metric name for failed requests counter
     */
    val failure: String = "sttp_requests_failure_count"

    /**
     * Metric name for request size summary
     */
    val requestSize: String = "sttp_request_size_bytes"

    /**
     * Metric name for response size summary
     */
    val responseSize: String = "sttp_response_size_bytes"
  }

  /**
   * Default histogram buckets for latency measurements in seconds. Covers common response times
   * from 5ms to 10s.
   */
  val DefaultBuckets: List[Double] = List(.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10)

  /**
   * Returns the HTTP method label for a request, used for metric labeling.
   *
   * @param req
   *   The STTP request
   * @return
   *   The HTTP method in uppercase (e.g., "GET", "POST")
   */
  def methodLabel(req: Request[?, ?]): String = req.method.method.toUpperCase

  /**
   * Returns the status label for a response, used for metric labeling.
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
  def statusLabel(rsp: Response[?]): String = {
    val code = rsp.code
    if (code.isInformational) "1xx"
    else if (code.isSuccess) "2xx"
    else if (code.isRedirect) "3xx"
    else if (code.isClientError) "4xx"
    else if (code.isServerError) "5xx"
    else code.code.toString
  }

  /**
   * Creates an STTP backend with custom metric mappers.
   *
   * This variant allows you to provide custom logic for mapping requests to specific metric
   * instances, giving you full control over metric collection and labeling.
   *
   * @param delegate
   *   The underlying STTP backend to wrap
   * @param latencyMapper
   *   Function to map a request and its outcome (the failure, or the response) to a histogram for
   *   recording latency. It is resolved when the outcome is known, so label values may depend on
   *   the response status.
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
  def apply[F[_]: Clock: MonadThrow, P](
    delegate: SttpBackend[F, P],
    latencyMapper: (Request[?, ?], Either[Throwable, Response[?]]) => Option[Histogram[F]],
    inProgressMapper: Request[?, ?] => Option[Gauge[F]],
    successMapper: (Request[?, ?], Response[?]) => Option[Counter[F]],
    errorMapper: (Request[?, ?], Response[?]) => Option[Counter[F]],
    failureMapper: (Request[?, ?], Throwable) => Option[Counter[F]],
    requestSizeMapper: Request[?, ?] => Option[Summary[F]],
    responseSizeMapper: (Request[?, ?], Response[?]) => Option[Summary[F]],
  ): SttpBackend[F, P] = {
    // redirects should be handled before prometheus
    new FollowRedirectsBackend[F, P](
      new ListenerBackend[F, P, State[F]](
        delegate,
        new PrometheusListener[F](
          latencyMapper = latencyMapper,
          inProgressMapper = inProgressMapper,
          successMapper = successMapper,
          errorMapper = errorMapper,
          failureMapper = failureMapper,
          requestSizeMapper = requestSizeMapper,
          responseSizeMapper = responseSizeMapper,
          discardFailure = discardMetricFailure,
        ),
      ),
    )
  }

  /**
   * Binary-compatible predecessor of the outcome-aware overload, kept for released API
   * compatibility. Unlike that overload, it cannot isolate failures of individual metric effects
   * (that requires `MonadThrow`, which this method cannot demand without breaking binary
   * compatibility).
   */
  @deprecated("Use the overload with an outcome-aware latencyMapper", "2.4.6")
  def apply[F[_]: Clock: Monad, P](
    delegate: SttpBackend[F, P],
    latencyMapper: Request[?, ?] => Option[Histogram[F]],
    inProgressMapper: Request[?, ?] => Option[Gauge[F]],
    successMapper: (Request[?, ?], Response[?]) => Option[Counter[F]],
    errorMapper: (Request[?, ?], Response[?]) => Option[Counter[F]],
    failureMapper: (Request[?, ?], Throwable) => Option[Counter[F]],
    requestSizeMapper: Request[?, ?] => Option[Summary[F]],
    responseSizeMapper: (Request[?, ?], Response[?]) => Option[Summary[F]],
  ): SttpBackend[F, P] = {
    // redirects should be handled before prometheus
    new FollowRedirectsBackend[F, P](
      new ListenerBackend[F, P, State[F]](
        delegate,
        new PrometheusListener[F](
          latencyMapper = { (request: Request[?, ?], _: Either[Throwable, Response[?]]) => latencyMapper(request) },
          inProgressMapper = inProgressMapper,
          successMapper = successMapper,
          errorMapper = errorMapper,
          failureMapper = failureMapper,
          requestSizeMapper = requestSizeMapper,
          responseSizeMapper = responseSizeMapper,
          discardFailure = identity,
        ),
      ),
    )
  }

  /**
   * Creates an STTP backend with automatic metric collection using a CollectorRegistry.
   *
   * This is the recommended way to create a metrics-enabled backend. It automatically sets up all
   * standard metrics with sensible defaults:
   *   - Request latency histogram with method label
   *   - In-progress requests gauge with method label
   *   - Success/error counters with method and status labels
   *   - Failure counter with method label
   *   - Request/response size summaries with appropriate labels
   *
   * The backend returns a Resource that properly manages the lifecycle of the metrics.
   *
   * There is a difference between PrometheusBackend and SmetricsBackend such that PrometheusBackend
   * uses metrics caching per collector registry per metrics name and type, while SmetricsBackend
   * creates new metrics each time it is called. So if you want to have caching behavior you need to
   * use [[CollectorRegistry.withCaching]] when creating SmetricsBackend. Or use uniq prefix to
   * avoid conflicts.
   *
   * Example usage:
   * {{{
   * import cats.effect.IO
   * import sttp.client3.*
   * import sttp.client3.smetrics.SmetricsBackend
   * import com.evolutiongaming.smetrics.CollectorRegistry
   *
   * val backend: SttpBackend[IO, Any] = ???
   * val registry: CollectorRegistry[IO] = ???
   *
   * SmetricsBackend.default1(backend, registry, prefix = Some("myapp_")).use { metricsBackend =>
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
   *   The metric name prefix (default: None)
   * @tparam F
   *   The effect type (e.g., IO, Task)
   * @tparam P
   *   The capabilities type for the backend
   * @return
   *   A Resource that manages the metrics-enabled backend lifecycle
   */
  def default1[F[_]: Clock: MonadThrow, P](
    delegate: SttpBackend[F, P],
    collectorRegistry: CollectorRegistry[F],
    prefix: Option[String] = None,
  ): Resource[F, SttpBackend[F, P]] = {
    val registry = prefix.fold(collectorRegistry)(collectorRegistry.prefixed(_))
    makeDefault(delegate, registry, discardMetricFailure)
  }

  /**
   * Binary-compatible predecessor of [[default1]], kept for released API compatibility. Unlike
   * [[default1]], it cannot isolate failures of individual metric effects: that requires
   * `MonadThrow`, which this method cannot demand without breaking binary compatibility, and a
   * `MonadThrow` overload under the same name would make every existing call site ambiguous
   * (overloads differing only in implicit parameters cannot be resolved).
   */
  @deprecated("Use default1, which also isolates metric recording failures", "2.4.6")
  def default[F[_]: Clock: Monad, P](
    delegate: SttpBackend[F, P],
    collectorRegistry: CollectorRegistry[F],
    prefix: Option[String] = None,
  ): Resource[F, SttpBackend[F, P]] = {
    val registry = prefix.fold(collectorRegistry)(collectorRegistry.prefixed(_))
    makeDefault(delegate, registry, identity[F[Unit]])
  }

  // metric recording must never fail the request, hence individual metric failures are
  // discarded and the remaining metrics are still recorded
  private def discardMetricFailure[F[_]: MonadThrow]: F[Unit] => F[Unit] =
    _.handleError(_ => ())

  private def makeDefault[F[_]: Clock: Monad, P](
    delegate: SttpBackend[F, P],
    collectorRegistry: CollectorRegistry[F],
    discardFailure: F[Unit] => F[Unit],
  ): Resource[F, SttpBackend[F, P]] =
    for {
      latency <- collectorRegistry.histogram(
        name = MetricNames.latency,
        help = "Request latency in seconds",
        buckets = Buckets(NonEmptyList.fromListUnsafe(DefaultBuckets)),
        labels = LabelNames("method"),
      )
      inProgress <- collectorRegistry.gauge(
        name = MetricNames.inProgress,
        help = "Number of requests in progress",
        labels = LabelNames("method"),
      )
      success <- collectorRegistry.counter(
        name = MetricNames.success,
        help = "Number of successful requests",
        labels = LabelNames("method", "status"),
      )
      error <- collectorRegistry.counter(
        name = MetricNames.error,
        help = "Number of errored requests",
        labels = LabelNames("method", "status"),
      )
      failure <- collectorRegistry.counter(
        name = MetricNames.failure,
        help = "Number of failed requests",
        labels = LabelNames("method"),
      )
      requestSize <- collectorRegistry.summary(
        name = MetricNames.requestSize,
        help = "Request size in bytes",
        labels = LabelNames("method"),
        quantiles = Quantiles.Default,
      )
      responseSize <- collectorRegistry.summary(
        name = MetricNames.responseSize,
        help = "Response size in bytes",
        labels = LabelNames("method", "status"),
        quantiles = Quantiles.Default,
      )
    } yield {
      // redirects should be handled before prometheus
      new FollowRedirectsBackend[F, P](
        new ListenerBackend[F, P, State[F]](
          delegate,
          new PrometheusListener[F](
            latencyMapper = { (req, _) => latency.labels(methodLabel(req)).some },
            inProgressMapper = { req => inProgress.labels(methodLabel(req)).some },
            successMapper = { (req, rsp) => success.labels(methodLabel(req), statusLabel(rsp)).some },
            errorMapper = { (req, rsp) => error.labels(methodLabel(req), statusLabel(rsp)).some },
            failureMapper = { (req, _) => failure.labels(methodLabel(req)).some },
            requestSizeMapper = { req => requestSize.labels(methodLabel(req)).some },
            responseSizeMapper = { (req, rsp) => responseSize.labels(methodLabel(req), statusLabel(rsp)).some },
            discardFailure = discardFailure,
          ),
        ),
      )
    }

  /**
   * Internal state passed between request lifecycle hooks.
   *
   * @param recordLatency
   *   Effect to record the request latency for a given request outcome
   * @param decInProgress
   *   Effect to decrement the in-progress requests gauge
   * @tparam F
   *   The effect type
   */
  private[this] final case class State[F[_]](
    recordLatency: Either[Throwable, Response[?]] => F[Unit],
    decInProgress: F[Unit],
  )

  /**
   * Internal RequestListener implementation that records metrics for HTTP requests.
   */
  private[this] class PrometheusListener[F[_]: Clock: Monad](
    latencyMapper: (Request[?, ?], Either[Throwable, Response[?]]) => Option[Histogram[F]],
    inProgressMapper: Request[?, ?] => Option[Gauge[F]],
    successMapper: (Request[?, ?], Response[?]) => Option[Counter[F]],
    errorMapper: (Request[?, ?], Response[?]) => Option[Counter[F]],
    failureMapper: (Request[?, ?], Throwable) => Option[Counter[F]],
    requestSizeMapper: Request[?, ?] => Option[Summary[F]],
    responseSizeMapper: (Request[?, ?], Response[?]) => Option[Summary[F]],
    // how to handle a failing metric effect: [[discardMetricFailure]] for the MonadThrow-based
    // entry points, identity (failures propagate, pre-2.4.6 behavior) for the released
    // Monad-based ones which cannot demand MonadThrow without breaking binary compatibility
    discardFailure: F[Unit] => F[Unit],
  ) extends RequestListener[F, State[F]] {

    private val unit = Applicative[F].unit

    override def beforeRequest(request: Request[?, ?]): F[State[F]] = {
      val requestSize = for {
        requestSize <- requestSizeMapper(request)
        size <- request.contentLength.map(_.toDouble)
      } yield requestSize.observe(size)

      val inProgress = inProgressMapper(request)

      for {
        elapsed <- MeasureDuration[F].start
        _ <- requestSize.getOrElse(unit)
        _ <- inProgress.map(_.inc()).getOrElse(unit)
      } yield State(
        recordLatency = recordLatency(request, _, elapsed),
        decInProgress = inProgress.map(_.dec()).getOrElse(unit),
      )
    }

    private def recordLatency(
      request: Request[?, ?],
      outcome: Either[Throwable, Response[?]],
      elapsed: F[FiniteDuration],
    ): F[Unit] =
      latencyMapper(request, outcome).fold(unit) { histogram =>
        elapsed.flatMap { elapsed => histogram.observe(elapsed.toUnit(SECONDS)) }
      }

    private def recordAll(effects: F[Unit]*): F[Unit] =
      effects.toList.traverse_(discardFailure)

    override def requestException(
      request: Request[?, ?],
      state: State[F],
      e: Exception,
    ): F[Unit] = {
      HttpError.find(e) match {
        case Some(HttpError(body, statusCode)) =>
          requestSuccessful(request, Response(body, statusCode).copy(request = request.onlyMetadata), state)
        case _ =>
          recordAll(
            state.recordLatency(e.asLeft),
            state.decInProgress,
            failureMapper(request, e).fold(unit)(_.inc()),
          )
      }
    }

    override def requestSuccessful(
      request: Request[?, ?],
      response: Response[?],
      state: State[F],
    ): F[Unit] = {
      val responseSize = for {
        responseSize <- responseSizeMapper(request, response)
        size <- response.contentLength.map(_.toDouble)
      } yield responseSize.observe(size)

      val counterMapper = if (response.isSuccess) successMapper else errorMapper

      recordAll(
        state.recordLatency(response.asRight),
        state.decInProgress,
        responseSize.getOrElse(unit),
        counterMapper(request, response).fold(unit)(_.inc()),
      )
    }

  }
}
