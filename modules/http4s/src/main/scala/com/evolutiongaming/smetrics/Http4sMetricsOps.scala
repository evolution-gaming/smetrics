package com.evolutiongaming.smetrics

import cats.Monad
import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.all._
import com.evolutiongaming.smetrics.MetricsHelper._
import org.http4s.metrics.TerminationType.{Abnormal, Canceled, Error, Timeout}
import org.http4s.metrics.{MetricsOps, TerminationType}
import org.http4s.{Method, Status}

object Http4sMetricsOps {

  def of[F[_] : Monad](
    collectorRegistry: CollectorRegistry[F],
    prefix: String = "http",
    histogramBuckets: Buckets = Buckets(NonEmptyList.of(.05, .1, .25, .5, 1, 2, 4, 8))
  ): Resource[F, MetricsOps[F]] =
    for {
      responseDuration <- collectorRegistry.histogram(
        s"${ prefix }_response_duration_seconds",
        "Response Duration in seconds.",
        histogramBuckets,
        LabelNames("classifier", "method", "phase")
      )
      activeRequests <- collectorRegistry.gauge(
        s"${ prefix }_active_request_count",
        "Total Active Requests.",
        LabelNames("classifier")
      )
      requests <- collectorRegistry.counter(
        s"${ prefix }_request_count",
        "Total Requests.",
        LabelNames("classifier", "method", "status")
      )
      abnormal <- collectorRegistry.histogram(
        s"${ prefix }_abnormal_terminations",
        "Total Abnormal Terminations.",
        histogramBuckets,
        LabelNames("classifier", "termination_type")
      )
    } yield {
      new MetricsOps[F] {
        override def increaseActiveRequests(classifier: Option[String]): F[Unit] =
          activeRequests.labels(reportClassifier(classifier)).inc()

        override def decreaseActiveRequests(classifier: Option[String]): F[Unit] =
          activeRequests.labels(reportClassifier(classifier)).dec()

        override def recordHeadersTime(method: Method, elapsed: Long, classifier: Option[String]): F[Unit] =
          responseDuration
            .labels(reportClassifier(classifier), reportMethod(method), reportPhase(Phase.Headers))
            .observe(elapsed.nanosToSeconds)

        override def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String]
        ): F[Unit] =
          responseDuration
            .labels(reportClassifier(classifier), reportMethod(method), reportPhase(Phase.Body))
            .observe(elapsed.nanosToSeconds) >>
            requests
              .labels(reportClassifier(classifier), reportMethod(method), reportStatus(status))
              .inc()

        override def recordAbnormalTermination(
          elapsed: Long,
          terminationType: TerminationType,
          classifier: Option[String]
        ): F[Unit] =
          abnormal
            .labels(reportClassifier(classifier), reportTermination(terminationType))
            .observe(elapsed.nanosToSeconds)
      }
    }

  private def reportStatus(status: Status): String =
    status.code match {
      case hundreds if hundreds < 200           => "1xx"
      case twohundreds if twohundreds < 300     => "2xx"
      case threehundreds if threehundreds < 400 => "3xx"
      case fourhundreds if fourhundreds < 500   => "4xx"
      case _                                    => "5xx"
    }

  private def reportClassifier(classifier: Option[String]): String = classifier.getOrElse("")

  private def reportMethod(m: Method): String = m match {
    case Method.GET     => "get"
    case Method.PUT     => "put"
    case Method.POST    => "post"
    case Method.HEAD    => "head"
    case Method.MOVE    => "move"
    case Method.OPTIONS => "options"
    case Method.TRACE   => "trace"
    case Method.CONNECT => "connect"
    case Method.DELETE  => "delete"
    case _              => "other"
  }

  private def reportTermination(t: TerminationType): String = t match {
    case Abnormal(_) => "abnormal"
    case Error(_)    => "error"
    case Timeout     => "timeout"
    case Canceled    => "canceled"
  }

  private def reportPhase(p: Phase): String = p match {
    case Phase.Headers => "headers"
    case Phase.Body    => "body"
  }

  private sealed trait Phase
  private object Phase {
    case object Headers extends Phase
    case object Body extends Phase
  }
}