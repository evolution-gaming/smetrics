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

object SmetricsBackend {

  object MetricNames {
    val DefaultPrefix                                        = "sttp_"
    def latency(prefix: String = DefaultPrefix): String      = s"${prefix}request_latency_seconds"
    def inProgress(prefix: String = DefaultPrefix): String   = s"${prefix}requests_in_progress"
    def success(prefix: String = DefaultPrefix): String      = s"${prefix}requests_success_count"
    def error(prefix: String = DefaultPrefix): String        = s"${prefix}requests_error_count"
    def failure(prefix: String = DefaultPrefix): String      = s"${prefix}requests_failure_count"
    def requestSize(prefix: String = DefaultPrefix): String  = s"${prefix}request_size_bytes"
    def responseSize(prefix: String = DefaultPrefix): String = s"${prefix}response_size_bytes"
  }

  val DefaultBuckets: List[Double] = List(.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10)

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
      new ListenerBackend[F, P, RequestCollectors[F]](
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
      def methodLabel(req: Request[_, _]): String = req.method.method.toUpperCase
      def statusLabel(rsp: Response[_]): String   = {
        val code = rsp.code
        if (code.isInformational) "1xx"
        else if (code.isSuccess) "2xx"
        else if (code.isRedirect) "3xx"
        else if (code.isClientError) "4xx"
        else if (code.isServerError) "5xx"
        else code.code.toString
      }

      // redirects should be handled before prometheus
      new FollowRedirectsBackend[F, P](
        new ListenerBackend[F, P, RequestCollectors[F]](
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

  private[this] final case class RequestCollectors[F[_]](recordLatency: F[Unit], decInProgress: F[Unit])

  private[this] class PrometheusListener[F[_]: Clock: Monad](
      latencyMapper: Request[_, _] => Option[Histogram[F]],
      inProgressMapper: Request[_, _] => Option[Gauge[F]],
      successMapper: (Request[_, _], Response[_]) => Option[Counter[F]],
      errorMapper: (Request[_, _], Response[_]) => Option[Counter[F]],
      failureMapper: (Request[_, _], Throwable) => Option[Counter[F]],
      requestSizeMapper: Request[_, _] => Option[Summary[F]],
      responseSizeMapper: (Request[_, _], Response[_]) => Option[Summary[F]],
  ) extends RequestListener[F, RequestCollectors[F]] {

    override def beforeRequest(request: Request[_, _]): F[RequestCollectors[F]] = {
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
      } yield RequestCollectors(
        recordLatency = recordLatency,
        decInProgress = inProgress.map(_.dec()).getOrElse(unit)
      )
    }

    override def requestException(
        request: Request[_, _],
        requestCollectors: RequestCollectors[F],
        e: Exception
    ): F[Unit] = {
      HttpError.find(e) match {
        case Some(HttpError(body, statusCode)) =>
          requestSuccessful(request, Response(body, statusCode).copy(request = request.onlyMetadata), requestCollectors)
        case _                                 =>
          for {
            _ <- requestCollectors.recordLatency
            _ <- requestCollectors.decInProgress
            _ <- failureMapper(request, e).map(_.inc()).sequence
          } yield ()
      }
    }

    override def requestSuccessful(
        request: Request[_, _],
        response: Response[_],
        requestCollectors: RequestCollectors[F]
    ): F[Unit] = {
      for {
        _      <- requestCollectors.recordLatency
        _      <- requestCollectors.decInProgress
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
