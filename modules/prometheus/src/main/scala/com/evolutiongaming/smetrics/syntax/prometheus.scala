package com.evolutiongaming.smetrics.syntax

import com.evolutiongaming.concurrent.CurrentThreadExecutionContext

import scala.concurrent.Future
import io.prometheus.client.{Collector, Histogram, Summary}

object prometheus {

  implicit class RichHistogram(val histogram: Histogram) extends AnyVal {

    def timeFuture[T](f: => Future[T]): Future[T] = {
      implicit val ec = CurrentThreadExecutionContext
      val start       = System.currentTimeMillis
      f andThen { case _ => histogram.observeTillNow(start) }
    }

    def timeFutureNanos[T](f: => Future[T]): Future[T] = {
      implicit val ec = CurrentThreadExecutionContext
      val start       = System.nanoTime
      f andThen { case _ => histogram.observeTillNowNanos(start) }
    }

    def observeTillNow(start: Long): Unit = {
      val end = System.currentTimeMillis
      histogram.observe((end - start).toDouble / Collector.MILLISECONDS_PER_SECOND)
    }

    def observeTillNowNanos(start: Long): Unit = {
      val end = System.nanoTime
      histogram.observe((end - start).toDouble / Collector.NANOSECONDS_PER_SECOND)
    }

  }

  implicit class RichHistogramChild(val histogramChild: Histogram.Child) extends AnyVal {

    def timeFuture[T](f: => Future[T]): Future[T] = {
      implicit val ec = CurrentThreadExecutionContext
      val start       = System.currentTimeMillis
      f andThen { case _ => histogramChild.observeTillNow(start) }
    }

    def timeFutureNanos[T](f: => Future[T]): Future[T] = {
      implicit val ec = CurrentThreadExecutionContext
      val start       = System.nanoTime
      f andThen { case _ => histogramChild.observeTillNowNanos(start) }
    }

    def observeTillNow(start: Long): Unit = {
      val end = System.currentTimeMillis
      histogramChild.observe((end - start).toDouble / Collector.MILLISECONDS_PER_SECOND)
    }

    def observeTillNowNanos(start: Long): Unit = {
      val end = System.nanoTime
      histogramChild.observe((end - start).toDouble / Collector.NANOSECONDS_PER_SECOND)
    }

  }

  implicit class RichSummaryBuilder(val summaryBuilder: Summary.Builder) extends AnyVal {

    def defaultQuantiles(): Summary.Builder = {
      summaryBuilder
        .quantile(0.5, 0.05)
        .quantile(0.9, 0.05)
        .quantile(0.95, 0.01)
        .quantile(0.99, 0.005)
    }

  }

  implicit class RichSummary(val summary: Summary) extends AnyVal {

    def timeFuture[T](f: => Future[T]): Future[T] = {
      implicit val ec = CurrentThreadExecutionContext
      val start       = System.currentTimeMillis
      f andThen { case _ => summary.observeTillNow(start) }
    }

    def timeFutureNanos[T](f: => Future[T]): Future[T] = {
      implicit val ec = CurrentThreadExecutionContext
      val start       = System.nanoTime
      f andThen { case _ => summary.observeTillNowNanos(start) }
    }

    def observeTillNow(start: Long): Unit = {
      val end = System.currentTimeMillis
      summary.observe((end - start).toDouble / Collector.MILLISECONDS_PER_SECOND)
    }

    def observeTillNowNanos(start: Long): Unit = {
      val end = System.nanoTime
      summary.observe((end - start).toDouble / Collector.NANOSECONDS_PER_SECOND)
    }

  }

  implicit class RichSummaryChild(val summaryChild: Summary.Child) extends AnyVal {

    def timeFuture[T](f: => Future[T]): Future[T] = {
      implicit val ec = CurrentThreadExecutionContext
      val start       = System.currentTimeMillis
      f andThen { case _ => summaryChild.observeTillNow(start) }
    }

    def timeFutureNanos[T](f: => Future[T]): Future[T] = {
      implicit val ec = CurrentThreadExecutionContext
      val start       = System.nanoTime
      f andThen { case _ => summaryChild.observeTillNowNanos(start) }
    }

    def observeTillNow(start: Long): Unit = {
      val end = System.currentTimeMillis
      summaryChild.observe((end - start).toDouble / Collector.MILLISECONDS_PER_SECOND)
    }

    def observeTillNowNanos(start: Long): Unit = {
      val end = System.nanoTime
      summaryChild.observe((end - start).toDouble / Collector.NANOSECONDS_PER_SECOND)
    }
  }
}
