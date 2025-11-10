package sttp3

import cats.effect.{Resource, Sync}
import com.evolutiongaming.smetrics.{CollectorRegistry, Counter, Histogram, Buckets}
import sttp.client3._
import sttp.monad.MonadError
import scala.concurrent.duration._

/**
 * PrometheusBackend for sttp3 using smetrics
 */
class PrometheusBackend[F[_], S](
  delegate: SttpBackend[F, S],
  requestsTotal: Counter[F],
  responsesTotal: Counter[F],
  errorsTotal: Counter[F],
  requestDuration: Histogram[F]
)(implicit F: Sync[F]) extends SttpBackend[F, S] {

  override def send(request: Request[_, S]): F[Response[Either[String, _]]] = {
    for {
      _ <- requestsTotal.inc()
      start <- F.delay(System.nanoTime())
      response <- delegate.send(request)
      end <- F.delay(System.nanoTime())
      duration = (end - start).nanos.toMillis.toDouble / 1000.0
      _ <- requestDuration.observe(duration)
      _ <- if (response.isSuccess) responsesTotal.inc() else errorsTotal.inc()
    } yield response
  }

  override def close(): F[Unit] = delegate.close()
}

object PrometheusBackend {
  def apply[F[_]: Sync, S](
    delegate: SttpBackend[F, S],
    registry: CollectorRegistry[F],
    prefix: String = "sttp3"
  ): Resource[F, PrometheusBackend[F, S]] = {
    for {
      requestsTotal <- registry.counterInitialized(s"${prefix}_requests_total", "Total requests", ())
      responsesTotal <- registry.counterInitialized(s"${prefix}_responses_total", "Total responses", ())
      errorsTotal <- registry.counterInitialized(s"${prefix}_errors_total", "Total errors", ())
      requestDuration <- registry.histogramInitialized(s"${prefix}_request_duration_seconds", "Request duration in seconds", Buckets.default, ())
    } yield new PrometheusBackend(delegate, requestsTotal, responsesTotal, errorsTotal, requestDuration)
  }
}

