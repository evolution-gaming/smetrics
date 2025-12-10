package com.evolutiongaming.smetrics

import cats.Monad
import cats.data.NonEmptyList
import cats.effect.*
import cats.syntax.all.*
import com.evolutiongaming.smetrics.MetricsHelper.*
import org.http4s.metrics.TerminationType.{Abnormal, Canceled, Error, Timeout}
import org.http4s.metrics.{MetricsOps, TerminationType}
import org.http4s.{Method, Status}

object Http4sMetricsOps {

  /**
   * Old, deprecated method. Uses summary for timed metrics. Doesn't allow to choose between summary
   * and histogram. Use [[histogram]] or [[summary]] directly instead.
   */
  @deprecated(message = "Use summary or histogram instead", since = "1.0.2")
  def of[F[_]: Monad](collectorRegistry: CollectorRegistry[F], prefix: String = "http"): Resource[F, MetricsOps[F]] =
    summary(collectorRegistry, prefix)

  /**
   * Difference with [[summary]] is only in using histogram instead of summary for timed metrics.
   */
  def histogram[F[_]: Monad](
    collectorRegistry: CollectorRegistry[F],
    prefix: String = "http",
    histogramBuckets: Buckets = Buckets(NonEmptyList.of(.05, .1, .25, .5, 1, 2, 4, 8)),
  ): Resource[F, MetricsOps[F]] =
    for {
      responseDuration <- collectorRegistry.histogram(
        s"${ prefix }_response_duration_seconds",
        "Response Duration in seconds.",
        histogramBuckets,
        LabelNames("classifier", "method", "phase"),
      )
      abnormal <- collectorRegistry.histogram(
        s"${ prefix }_abnormal_terminations",
        "Total Abnormal Terminations.",
        histogramBuckets,
        LabelNames("classifier", "termination_type"),
      )
      metricOps <- create(collectorRegistry, prefix, responseDuration, abnormal)
    } yield metricOps

  /**
   * Difference with [[histogram]] is only in using summary instead of histogram for timed metrics
   */
  def summary[F[_]: Monad](
    collectorRegistry: CollectorRegistry[F],
    prefix: String = "http",
    quantiles: Quantiles = Quantiles.Default,
  ): Resource[F, MetricsOps[F]] =
    for {
      responseDuration <- collectorRegistry.summary(
        s"${ prefix }_response_duration_seconds",
        "Response Duration in seconds.",
        quantiles,
        LabelNames("classifier", "method", "phase"),
      )
      abnormal <- collectorRegistry.summary(
        s"${ prefix }_abnormal_terminations",
        "Total Abnormal Terminations.",
        quantiles,
        LabelNames("classifier", "termination_type"),
      )
      metricOps <- create(collectorRegistry, prefix, responseDuration, abnormal)
    } yield metricOps

  private def create[F[_]: Monad, A](
    collectorRegistry: CollectorRegistry[F],
    prefix: String,
    responseDuration: LabelValues.`3`[A],
    abnormal: LabelValues.`2`[A],
  )(implicit
    observable: Observable[F, A],
  ): Resource[F, MetricsOps[F]] =
    for {
      activeRequests <- collectorRegistry.gauge(
        s"${ prefix }_active_request_count",
        "Total Active Requests.",
        LabelNames("classifier"),
      )
      requests <- collectorRegistry.counter(
        s"${ prefix }_request_count",
        "Total Requests.",
        LabelNames("classifier", "method", "status"),
      )
    } yield new MetricsOps[F] {
      override def increaseActiveRequests(classifier: Option[String]): F[Unit] =
        activeRequests.labels(reportClassifier(classifier)).inc()

      override def decreaseActiveRequests(classifier: Option[String]): F[Unit] =
        activeRequests.labels(reportClassifier(classifier)).dec()

      override def recordHeadersTime(method: Method, elapsed: Long, classifier: Option[String]): F[Unit] = {
        val responseDurationLabeled = responseDuration
          .labels(reportClassifier(classifier), reportMethod(method), reportPhase(Phase.Headers))
        observable.observe(responseDurationLabeled, elapsed.nanosToSeconds)
      }

      override def recordTotalTime(
        method: Method,
        status: Status,
        elapsed: Long,
        classifier: Option[String],
      ): F[Unit] = {
        val responseDurationLabeled = responseDuration
          .labels(reportClassifier(classifier), reportMethod(method), reportPhase(Phase.Body))
        observable.observe(responseDurationLabeled, elapsed.nanosToSeconds) >>
          requests
            .labels(reportClassifier(classifier), reportMethod(method), reportStatus(status))
            .inc()
      }

      override def recordAbnormalTermination(
        elapsed: Long,
        terminationType: TerminationType,
        classifier: Option[String],
      ): F[Unit] = {
        val abnormalLabeled = abnormal.labels(reportClassifier(classifier), reportTermination(terminationType))
        observable.observe(abnormalLabeled, elapsed.nanosToSeconds)
      }
    }

  /**
   * Just a wrapper around histogram and summary so we can abstract over them in [[create]].
   */
  private trait Observable[F[_], A] {
    def observe(metric: A, value: Double): F[Unit]
  }
  private implicit def summaryObservable[F[_]]: Observable[F, Summary[F]] = new Observable[F, Summary[F]] {
    override def observe(metric: Summary[F], value: Double): F[Unit] = metric.observe(value)
  }
  private implicit def histogramObservable[F[_]]: Observable[F, Histogram[F]] = new Observable[F, Histogram[F]] {
    override def observe(metric: Histogram[F], value: Double): F[Unit] = metric.observe(value)
  }

  private def reportStatus(status: Status): String =
    status.code match {
      case hundreds if hundreds < 200 => "1xx"
      case twohundreds if twohundreds < 300 => "2xx"
      case threehundreds if threehundreds < 400 => "3xx"
      case fourhundreds if fourhundreds < 500 => "4xx"
      case _ => "5xx"
    }

  private def reportClassifier(classifier: Option[String]): String = classifier.getOrElse("")

  private def reportMethod(m: Method): String = m match {
    case Method.GET => "get"
    case Method.PUT => "put"
    case Method.POST => "post"
    case Method.HEAD => "head"
    case Method.MOVE => "move"
    case Method.OPTIONS => "options"
    case Method.TRACE => "trace"
    case Method.CONNECT => "connect"
    case Method.DELETE => "delete"
    case _ => "other"
  }

  private def reportTermination(t: TerminationType): String = t match {
    case Abnormal(_) => "abnormal"
    case Error(_) => "error"
    case Timeout => "timeout"
    case Canceled => "canceled"
  }

  private def reportPhase(p: Phase): String = p match {
    case Phase.Headers => "headers"
    case Phase.Body => "body"
  }

  private sealed trait Phase
  private object Phase {
    case object Headers extends Phase
    case object Body extends Phase
  }
}
