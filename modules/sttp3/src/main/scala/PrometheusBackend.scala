package sttp3

import cats.effect.{Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.evolutiongaming.smetrics.{CollectorRegistry, Counter, Histogram, Buckets, LabelsInitialized}
import sttp.client3._
import sttp.monad.MonadError
import scala.concurrent.duration._

class PrometheusBackend[F[_]: Sync, P](
  delegate: SttpBackend[F, P],
  requestsTotal: Counter[F],
  responsesTotal: Counter[F],
  errorsTotal: Counter[F],
  requestDuration: Histogram[F]
) extends SttpBackend[F, P] {

  override def send[T, R >: P with sttp.capabilities.Effect[F]](request: Request[T, R]): F[Response[T]] = {
    for {
      _ <- requestsTotal.inc()
      start <- Sync[F].delay(System.nanoTime())
      response <- delegate.send(request)
      end <- Sync[F].delay(System.nanoTime())
      duration = (end - start).nanos.toMillis.toDouble / 1000.0
      _ <- requestDuration.observe(duration)
      _ <- if (response.code.isSuccess) responsesTotal.inc() else errorsTotal.inc()
    } yield response
  }

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[F] = delegate.responseMonad
}

object PrometheusBackend {
  private val defaultBuckets = Buckets.linear(0.0, 0.5, 20)
  def apply[F[_]: Sync, P](
    delegate: SttpBackend[F, P],
    registry: CollectorRegistry[F],
    prefix: String = "sttp3"
  ): Resource[F, PrometheusBackend[F, P]] = {
    for {
      requestsTotal <- registry.counterInitialized(s"${prefix}_requests_total", "Total requests", LabelsInitialized())
      responsesTotal <- registry.counterInitialized(s"${prefix}_responses_total", "Total responses", LabelsInitialized())
      errorsTotal <- registry.counterInitialized(s"${prefix}_errors_total", "Total errors", LabelsInitialized())
      requestDuration <- registry.histogramInitialized(s"${prefix}_request_duration_seconds", "Request duration in seconds", defaultBuckets, LabelsInitialized())
    } yield new PrometheusBackend(delegate, requestsTotal, responsesTotal, errorsTotal, requestDuration)
  }
}
