package sttp.client4.smetrics

import cats.*
import cats.data.NonEmptyList
import cats.effect.{Clock, Resource}
import cats.syntax.all.*
import com.evolutiongaming.catshelper.MeasureDuration
import com.evolutiongaming.catshelper.ToTry
import com.evolutiongaming.smetrics.*
import sttp.client4.*
import sttp.client4.listener.*
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.model.ResponseMetadata

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.{FiniteDuration, SECONDS}

/**
 * Factory for creating STTP backends that record metrics using smetrics.
 *
 * This backend wraps an existing STTP backend and records various metrics about HTTP requests,
 * following the Prometheus naming conventions for HTTP client metrics:
 *   - '''Request duration''' (histogram): Records the time taken for each request in seconds
 *   - '''Requests active''' (gauge): Tracks the number of currently active requests
 *   - '''Success/error/failure counts''' (counters): Counts successful (2xx), error (4xx/5xx), and
 *     failed (exception) requests
 *   - '''Request and response sizes''' (summaries): Records the size of request and response bodies
 *     in bytes
 *
 * ==Metric Names==
 *
 * Metrics are named following the Prometheus naming best practices:
 *   - `http_client_request_duration_seconds` - Request duration histogram
 *   - `http_client_requests_active` - Gauge of active requests
 *   - `http_client_requests_success` - Counter of successful requests (2xx)
 *   - `http_client_requests_error` - Counter of error requests (4xx/5xx)
 *   - `http_client_requests_failure` - Counter of failed requests (exceptions)
 *   - `http_client_request_size_bytes` - Summary of request body sizes
 *   - `http_client_response_size_bytes` - Summary of response body sizes
 *
 * ==Usage==
 *
 * The simplest way to use this backend is with a `CollectorRegistry`:
 *
 * {{{
 * import cats.effect.IO
 * import sttp.client4.*
 * import sttp.client4.smetrics.SmetricsBackend
 * import com.evolutiongaming.smetrics.CollectorRegistry
 *
 * val backend: Backend[IO] = ???
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
 * SmetricsBackend.default1(backend, registry, prefix = Some("myapp_"))
 * }}}
 *
 * This will generate metrics like:
 *   - `myapp_http_client_request_duration_seconds`
 *   - `myapp_http_client_requests_active`
 *   - `myapp_http_client_requests_success`
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
 *   durationMapper = { (req, outcome) => ??? },
 *   activeMapper = ???,
 *   successMapper = ???,
 *   errorMapper = ???,
 *   failureMapper = ???,
 *   requestSizeMapper = ???,
 *   responseSizeMapper = ???
 * )
 * }}}
 *
 * ==Custom Recording==
 *
 * For example size mappers are typed as [[com.evolutiongaming.smetrics.Summary]], but using an actual
 * summary collector is not mandatory: `Summary` has a single `observe` method, so any recording
 * strategy can be plugged in by implementing it. For example, recording sizes as a histogram
 *
 * {{{
 * def sizeAsHistogram(histogram: Histogram[F]): Summary[F] =
 *   new Summary[F] {
 *     def observe(value: Double): F[Unit] =
 *       histogram.observe(value)
 *   }
 *
 * val backend = SmetricsBackend(
 *   delegate = underlyingBackend,
 *   requestSizeMapper = { req =>
 *     sizeAsHistogram(
 *       requestSizeHistogram.labels(methodLabel(req))
 *     ).some
 *   },
 *   ...
 * )
 * }}}
 * 
 * Returning `None` from a mapper disables that metric entirely.
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
 * ==WebSockets==
 *
 * Durations are intentionally not recorded for WebSocket requests, mirroring sttp's own
 * `PrometheusBackend`: the measured time would cover the whole socket lifetime rather than a
 * request-response roundtrip. All other metrics are recorded for WebSocket requests as usual.
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
   *
   * These names follow the Prometheus naming conventions for HTTP client metrics. See:
   * https://prometheus.io/docs/practices/naming/
   */
  object MetricNames {

    /**
     * Metric name for request duration histogram. Measures HTTP client request duration in seconds.
     */
    val duration: String = "http_client_request_duration_seconds"

    /**
     * Metric name for active requests gauge. Tracks the number of HTTP client requests currently in
     * progress.
     */
    val active: String = "http_client_requests_active"

    /**
     * Metric name for successful requests counter. Counts HTTP client requests that completed
     * successfully (2xx status).
     */
    val success: String = "http_client_requests_success"

    /**
     * Metric name for errored requests counter. Counts HTTP client requests that resulted in an
     * error (4xx/5xx status).
     */
    val error: String = "http_client_requests_error"

    /**
     * Metric name for failed requests counter. Counts HTTP client requests that failed with an
     * exception.
     */
    val failure: String = "http_client_requests_failure"

    /**
     * Metric name for request size summary. Records HTTP client request body size in bytes.
     */
    val requestSize: String = "http_client_request_size_bytes"

    /**
     * Metric name for response size summary. Records HTTP client response body size in bytes.
     */
    val responseSize: String = "http_client_response_size_bytes"
  }

  /**
   * Default histogram buckets for duration measurements in seconds. Covers common response times
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
  def methodLabel(req: GenericRequest[?, ?]): String = req.method.method.toUpperCase

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
   *   The STTP response metadata
   * @return
   *   The status label string (e.g., "2xx", "404")
   */
  def statusLabel(rsp: ResponseMetadata): String = {
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
   * @param durationMapper
   *   Function to map a request and its outcome (the failure, or the response metadata) to a
   *   histogram for recording request duration. It is resolved when the outcome is known, so label
   *   values may depend on the response status. Durations are not recorded for WebSocket requests.
   * @param activeMapper
   *   Function to map a request to a gauge for tracking active requests
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
   * @return
   *   A new backend that records metrics according to the provided mappers
   */
  def apply[F[_]: Clock: MonadThrow: ToTry](
    delegate: Backend[F],
    durationMapper: (GenericRequest[?, ?], Either[Throwable, ResponseMetadata]) => Option[Histogram[F]],
    activeMapper: GenericRequest[?, ?] => Option[Gauge[F]],
    successMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Counter[F]],
    errorMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Counter[F]],
    failureMapper: (GenericRequest[?, ?], Throwable) => Option[Counter[F]],
    requestSizeMapper: GenericRequest[?, ?] => Option[Summary[F]],
    responseSizeMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Summary[F]],
  ): Backend[F] = {
    // redirects should be handled before metrics collection
    FollowRedirectsBackend(
      ListenerBackend(
        delegate = delegate,
        listener = new SmetricsListener[F](
          durationMapper = durationMapper,
          activeMapper = activeMapper,
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

  def apply[F[_]: Clock: MonadThrow: ToTry, C](
    delegate: StreamBackend[F, C],
    durationMapper: (GenericRequest[?, ?], Either[Throwable, ResponseMetadata]) => Option[Histogram[F]],
    activeMapper: GenericRequest[?, ?] => Option[Gauge[F]],
    successMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Counter[F]],
    errorMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Counter[F]],
    failureMapper: (GenericRequest[?, ?], Throwable) => Option[Counter[F]],
    requestSizeMapper: GenericRequest[?, ?] => Option[Summary[F]],
    responseSizeMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Summary[F]],
  ): StreamBackend[F, C] = {
    // redirects should be handled before metrics collection
    FollowRedirectsBackend(
      ListenerBackend(
        delegate = delegate,
        listener = new SmetricsListener[F](
          durationMapper = durationMapper,
          activeMapper = activeMapper,
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
  @deprecated("Use the overload with an outcome-aware durationMapper", "2.4.6")
  def apply[F[_]: Clock: Monad: ToTry](
    delegate: Backend[F],
    durationMapper: GenericRequest[?, ?] => Option[Histogram[F]],
    activeMapper: GenericRequest[?, ?] => Option[Gauge[F]],
    successMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Counter[F]],
    errorMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Counter[F]],
    failureMapper: (GenericRequest[?, ?], Throwable) => Option[Counter[F]],
    requestSizeMapper: GenericRequest[?, ?] => Option[Summary[F]],
    responseSizeMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Summary[F]],
  ): Backend[F] = {
    // redirects should be handled before metrics collection
    FollowRedirectsBackend(
      ListenerBackend(
        delegate = delegate,
        listener = new SmetricsListener[F](
          durationMapper = {
            (request: GenericRequest[?, ?], _: Either[Throwable, ResponseMetadata]) => durationMapper(request)
          },
          activeMapper = activeMapper,
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
   * Binary-compatible predecessor of the outcome-aware overload, kept for released API
   * compatibility. Unlike that overload, it cannot isolate failures of individual metric effects
   * (that requires `MonadThrow`, which this method cannot demand without breaking binary
   * compatibility).
   */
  @deprecated("Use the overload with an outcome-aware durationMapper", "2.4.6")
  def apply[F[_]: Clock: Monad: ToTry, C](
    delegate: StreamBackend[F, C],
    durationMapper: GenericRequest[?, ?] => Option[Histogram[F]],
    activeMapper: GenericRequest[?, ?] => Option[Gauge[F]],
    successMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Counter[F]],
    errorMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Counter[F]],
    failureMapper: (GenericRequest[?, ?], Throwable) => Option[Counter[F]],
    requestSizeMapper: GenericRequest[?, ?] => Option[Summary[F]],
    responseSizeMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Summary[F]],
  ): StreamBackend[F, C] = {
    // redirects should be handled before metrics collection
    FollowRedirectsBackend(
      ListenerBackend(
        delegate = delegate,
        listener = new SmetricsListener[F](
          durationMapper = {
            (request: GenericRequest[?, ?], _: Either[Throwable, ResponseMetadata]) => durationMapper(request)
          },
          activeMapper = activeMapper,
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
   *   - Request duration histogram with method label
   *   - Active requests gauge with method label
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
   * import sttp.client4.*
   * import sttp.client4.smetrics.SmetricsBackend
   * import com.evolutiongaming.smetrics.CollectorRegistry
   *
   * val backend: Backend[IO] = ???
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
   * @return
   *   A Resource that manages the metrics-enabled backend lifecycle
   */
  def default1[F[_]: Clock: MonadThrow: ToTry](
    delegate: Backend[F],
    collectorRegistry: CollectorRegistry[F],
    prefix: Option[String] = None,
  ): Resource[F, Backend[F]] = {
    val registry = prefix.fold(collectorRegistry)(collectorRegistry.prefixed(_))
    makeDefaultSmetricsListener(registry, discardMetricFailure).map { listener =>
      FollowRedirectsBackend(
        ListenerBackend(
          delegate,
          listener,
        ),
      )
    }
  }

  def default1[F[_]: Clock: MonadThrow: ToTry, C](
    delegate: StreamBackend[F, C],
    collectorRegistry: CollectorRegistry[F],
    prefix: Option[String],
  ): Resource[F, StreamBackend[F, C]] = {
    val registry = prefix.fold(collectorRegistry)(collectorRegistry.prefixed(_))
    makeDefaultSmetricsListener(registry, discardMetricFailure).map { listener =>
      FollowRedirectsBackend(
        ListenerBackend(
          delegate,
          listener,
        ),
      )
    }
  }

  /**
   * Binary-compatible predecessor of [[default1]], kept for released API compatibility. Unlike
   * [[default1]], it cannot isolate failures of individual metric effects: that requires
   * `MonadThrow`, which this method cannot demand without breaking binary compatibility, and a
   * `MonadThrow` overload under the same name would make every existing call site ambiguous
   * (overloads differing only in implicit parameters cannot be resolved).
   */
  @deprecated("Use default1, which also isolates metric recording failures", "2.4.6")
  def default[F[_]: Clock: Monad: ToTry](
    delegate: Backend[F],
    collectorRegistry: CollectorRegistry[F],
    prefix: Option[String] = None,
  ): Resource[F, Backend[F]] = {
    val registry = prefix.fold(collectorRegistry)(collectorRegistry.prefixed(_))
    makeDefaultSmetricsListener(registry, identity[F[Unit]]).map { listener =>
      FollowRedirectsBackend(
        ListenerBackend(
          delegate,
          listener,
        ),
      )
    }
  }

  /**
   * Binary-compatible predecessor of [[default1]], kept for released API compatibility. Unlike
   * [[default1]], it cannot isolate failures of individual metric effects: that requires
   * `MonadThrow`, which this method cannot demand without breaking binary compatibility, and a
   * `MonadThrow` overload under the same name would make every existing call site ambiguous
   * (overloads differing only in implicit parameters cannot be resolved).
   */
  @deprecated("Use default1, which also isolates metric recording failures", "2.4.6")
  def default[F[_]: Clock: Monad: ToTry, C](
    delegate: StreamBackend[F, C],
    collectorRegistry: CollectorRegistry[F],
    prefix: Option[String],
  ): Resource[F, StreamBackend[F, C]] = {
    val registry = prefix.fold(collectorRegistry)(collectorRegistry.prefixed(_))
    makeDefaultSmetricsListener(registry, identity[F[Unit]]).map { listener =>
      FollowRedirectsBackend(
        ListenerBackend(
          delegate,
          listener,
        ),
      )
    }
  }

  // metric recording must never fail the request, hence individual metric failures are
  // discarded and the remaining metrics are still recorded
  private def discardMetricFailure[F[_]: MonadThrow]: F[Unit] => F[Unit] =
    _.handleError(_ => ())

  private def makeDefaultSmetricsListener[F[_]: Clock: Monad: ToTry](
    collectorRegistry: CollectorRegistry[F],
    discardFailure: F[Unit] => F[Unit],
  ): Resource[F, SmetricsListener[F]] =
    for {
      duration <- collectorRegistry.histogram(
        name = MetricNames.duration,
        help = "HTTP client request duration in seconds",
        buckets = Buckets(NonEmptyList.fromListUnsafe(DefaultBuckets)),
        labels = LabelNames("method"),
      )
      active <- collectorRegistry.gauge(
        name = MetricNames.active,
        help = "Number of HTTP client requests currently in progress",
        labels = LabelNames("method"),
      )
      success <- collectorRegistry.counter(
        name = MetricNames.success,
        help = "Number of successful HTTP client requests (2xx status)",
        labels = LabelNames("method", "status"),
      )
      error <- collectorRegistry.counter(
        name = MetricNames.error,
        help = "Number of HTTP client requests that resulted in an error (4xx/5xx status)",
        labels = LabelNames("method", "status"),
      )
      failure <- collectorRegistry.counter(
        name = MetricNames.failure,
        help = "Number of HTTP client requests that failed with an exception",
        labels = LabelNames("method"),
      )
      requestSize <- collectorRegistry.summary(
        name = MetricNames.requestSize,
        help = "HTTP client request body size in bytes",
        labels = LabelNames("method"),
        quantiles = Quantiles.Default,
      )
      responseSize <- collectorRegistry.summary(
        name = MetricNames.responseSize,
        help = "HTTP client response body size in bytes",
        labels = LabelNames("method", "status"),
        quantiles = Quantiles.Default,
      )
    } yield new SmetricsListener[F](
      durationMapper = { (req, _) => duration.labels(methodLabel(req)).some },
      activeMapper = { req => active.labels(methodLabel(req)).some },
      successMapper = { (req, rsp) => success.labels(methodLabel(req), statusLabel(rsp)).some },
      errorMapper = { (req, rsp) => error.labels(methodLabel(req), statusLabel(rsp)).some },
      failureMapper = { (req, _) => failure.labels(methodLabel(req)).some },
      requestSizeMapper = { req => requestSize.labels(methodLabel(req)).some },
      responseSizeMapper = { (req, rsp) => responseSize.labels(methodLabel(req), statusLabel(rsp)).some },
      discardFailure = discardFailure,
    )

  /**
   * Internal state passed between request lifecycle hooks.
   *
   * @param recordDuration
   *   Effect to record the request duration for a given request outcome
   * @param decActive
   *   Effect to decrement the active requests gauge
   * @param recorded
   *   Guard ensuring metrics are recorded exactly once per request: depending on the response
   *   handling description, [[sttp.client4.listener.RequestListener]] may invoke several of the
   *   completion hooks for a single request, or any one of them
   * @tparam F
   *   The effect type
   */
  private[this] final case class State[F[_]](
    recordDuration: Either[Throwable, ResponseMetadata] => F[Unit],
    decActive: F[Unit],
    // AtomicBoolean instead of Ref[F, Boolean]: creating a Ref requires a Sync/Ref.Make constraint,
    // which cannot be added without breaking binary compatibility of the released API; if bin-compat
    // constraints are ever dropped (e.g. in a 3.0), Ref[F, Boolean] + getAndSet would be the
    // idiomatic replacement
    recorded: AtomicBoolean,
  )

  /**
   * Internal RequestListener implementation that records metrics for HTTP requests.
   *
   * Metrics are recorded exactly once per request, by whichever completion hook fires first:
   *   - `responseBodyReceived` for safe, non-WebSocket requests whose body was fully received
   *   - `responseHandled` as the fallback for WebSockets, response exceptions raised before the
   *     body was received, and streaming bodies that are never fully consumed
   *   - `exception` for requests that failed without response metadata (including cancellation)
   */
  private[this] class SmetricsListener[F[_]: Clock: Monad: ToTry](
    durationMapper: (GenericRequest[?, ?], Either[Throwable, ResponseMetadata]) => Option[Histogram[F]],
    activeMapper: GenericRequest[?, ?] => Option[Gauge[F]],
    successMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Counter[F]],
    errorMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Counter[F]],
    failureMapper: (GenericRequest[?, ?], Throwable) => Option[Counter[F]],
    requestSizeMapper: GenericRequest[?, ?] => Option[Summary[F]],
    responseSizeMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[Summary[F]],
    // how to handle a failing metric effect: [[discardMetricFailure]] for the MonadThrow-based
    // entry points, identity (failures propagate, pre-2.4.6 behavior) for the released
    // Monad-based ones which cannot demand MonadThrow without breaking binary compatibility
    discardFailure: F[Unit] => F[Unit],
  ) extends RequestListener[F, State[F]] {

    private val unit = Applicative[F].unit

    override def before(request: GenericRequest[?, ?]): F[State[F]] = {
      val requestSize = for {
        size <- request.contentLength.map(_.toDouble)
        summary <- requestSizeMapper(request)
      } yield summary.observe(size)

      val active = activeMapper(request)

      for {
        elapsed <- MeasureDuration[F].start
        _ <- requestSize.getOrElse(unit)
        _ <- active.map(_.inc()).getOrElse(unit)
      } yield State(
        recordDuration = recordDuration(request, _, elapsed),
        decActive = active.map(_.dec()).getOrElse(unit),
        recorded = new AtomicBoolean(false),
      )
    }

    // durations are intentionally not recorded for WebSocket requests, mirroring sttp's own
    // PrometheusBackend: the measured time would cover the whole socket lifetime rather than
    // a request-response roundtrip
    private def recordDuration(
      request: GenericRequest[?, ?],
      outcome: Either[Throwable, ResponseMetadata],
      elapsed: F[FiniteDuration],
    ): F[Unit] =
      if (request.isWebSocket) unit
      else
        durationMapper(request, outcome).fold(unit) { histogram =>
          elapsed.flatMap { elapsed => histogram.observe(elapsed.toUnit(SECONDS)) }
        }

    private def recordAll(effects: F[Unit]*): F[Unit] =
      effects.toList.traverse_(discardFailure)

    private def recordOnce(state: State[F])(record: => F[Unit]): F[Unit] =
      unit.flatMap { _ =>
        if (state.recorded.compareAndSet(false, true)) record else unit
      }

    private def captureResponseMetrics(
      request: GenericRequest[?, ?],
      response: ResponseMetadata,
      state: State[F],
    ): F[Unit] = {
      val responseSize = for {
        size <- response.contentLength.map(_.toDouble)
        summary <- responseSizeMapper(request, response)
      } yield summary.observe(size)

      val counterMapper = if (response.isSuccess) successMapper else errorMapper

      recordAll(
        state.recordDuration(response.asRight),
        state.decActive,
        responseSize.getOrElse(unit),
        counterMapper(request, response).fold(unit)(_.inc()),
      )
    }

    override def responseBodyReceived(
      request: GenericRequest[?, ?],
      response: ResponseMetadata,
      state: State[F],
    ): Unit =
      // the listener contract forces a synchronous signature here, so the effect is run via ToTry;
      // the resulting Try is discarded as captureResponseMetrics already handles metric failures
      if (state.recorded.compareAndSet(false, true)) {
        val _ = ToTry[F].apply(captureResponseMetrics(request, response, state))
      }

    // fallback for requests where responseBodyReceived never fires: WebSockets, response
    // exceptions raised before the body was received, and streaming bodies that are never
    // fully consumed
    override def responseHandled(
      request: GenericRequest[?, ?],
      response: ResponseMetadata,
      state: State[F],
      e: Option[ResponseException[?]],
    ): F[Unit] =
      recordOnce(state) {
        captureResponseMetrics(request, response, state)
      }

    override def exception(
      request: GenericRequest[?, ?],
      state: State[F],
      e: Throwable,
      responseBodyReceivedCalled: Boolean,
    ): F[Unit] =
      recordOnce(state) {
        recordAll(
          state.recordDuration(e.asLeft),
          state.decActive,
          failureMapper(request, e).fold(unit)(_.inc()),
        )
      }
  }
}
