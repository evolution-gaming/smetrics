package com.evolutiongaming.smetrics

import cats.arrow.FunctionK
import cats.data.{NonEmptyList => Nel}
import cats.effect.{IO, Sync}
import cats.implicits._
import com.evolutiongaming.smetrics.IOSuite._
import io.prometheus.{client => P}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class CollectorRegistryPrometheusSpec extends AsyncFunSuite with Matchers {

  import CollectorRegistryPrometheusSpec._

  test("gauge") {
    testGauge[IO].run()
  }

  test("gaugeWithInitialLabels") {
    testGaugeWithInitialLabels[IO].run()
  }

  test("counter") {
    testCounter[IO].run()
  }

  test("counterWithInitialLabels") {
    testCounterWithInitialLabels[IO].run()
  }

  test("summary") {
    testSummary[IO].run()
  }

  test("summaryWithInitialLabels") {
    testSummaryWithInitialLabels[IO].run()
  }

  test("histogram") {
    testHistogram[IO].run()
  }

  test("histogramWithInitialLabels") {
    testHistogramWithInitialLabels[IO].run()
  }

  private def testGauge[F[_] : Sync] = {

    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val gauge = registry.gauge(
      name = "gauge",
      help = "help_test",
      labels = LabelNames("l1"))

    def value(value: String) = {
      registryP.value[F]("gauge", Nel.of("l1"), Nel.of(value))
    }

    gauge.mapK(FunctionK.id[F]).use { gauge =>
      for {
        v1 <- value("v1")
        _ <- gauge.labels("v1").set(2.0)
        _ <- gauge.labels("v2").inc(2.0)
        _ <- gauge.labels("v2").dec(1.0)
        v2 <- value("v1")
        v3 <- value("v2")
        v4 <- value("v3")
      } yield {
        v1 shouldEqual None
        v2 shouldEqual Some(2.0)
        v3 shouldEqual Some(1.0)
        v4 shouldEqual None
      }
    }
  }


  private def testGaugeWithInitialLabels[F[_] : Sync] = {

    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val gauge = registry.gaugeWithInitialLabels(
      name = "gauge",
      help = "help_test",
      labels = LabelNames("l1")
    ).labelValues(Nel.of("v1", "v2", "v3"))

    def value(value: String) = {
      registryP.value[F]("gauge", Nel.of("l1"), Nel.of(value))
    }

    gauge.mapK(FunctionK.id[F]).use { gauge =>
      for {
        v1 <- value("v1")
        _ <- gauge.labels("v1").set(2.0)
        _ <- gauge.labels("v2").inc(2.0)
        _ <- gauge.labels("v2").dec(1.0)
        v2 <- value("v1")
        v3 <- value("v2")
        v4 <- value("v3")
      } yield {
        v1 shouldEqual Some(0.0)
        v2 shouldEqual Some(2.0)
        v3 shouldEqual Some(1.0)
        v4 shouldEqual Some(0.0)
      }
    }
  }


  private def testCounter[F[_] : Sync] = {

    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val counter = registry.counter(
      name = "counter",
      help = "help_test",
      labels = LabelNames("l1", "l2"))

    def value(value: String) = {
      registryP.value[F]("counter", Nel.of("l1", "l2"), Nel.of(value, value))
    }

    counter.mapK(FunctionK.id[F]).use { counter =>
      for {
        v1 <- value("v1")
        _ <- counter.labels("v1", "v1").inc(2.0)
        _ <- counter.labels("v1", "v1").inc()
        _ <- counter.labels("v2", "v2").inc(1.0)
        v2 <- value("v1")
        v3 <- value("v2")
        v4 <- value("v3")
      } yield {
        v1 shouldEqual None
        v2 shouldEqual Some(3.0)
        v3 shouldEqual Some(1.0)
        v4 shouldEqual None
      }
    }
  }


  private def testCounterWithInitialLabels[F[_] : Sync] = {

    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val counter = registry.counterWithInitialLabels(
      name = "counter",
      help = "help_test",
      labels = LabelNames("l1", "l2")
    ).labelValues(Nel.of("v1", "v2", "v3"), Nel.of("v1", "v2", "v3"))

    def value(value: String) = {
      registryP.value[F]("counter", Nel.of("l1", "l2"), Nel.of(value, value))
    }

    counter.mapK(FunctionK.id[F]).use { counter =>
      for {
        v1 <- value("v1")
        _ <- counter.labels("v1", "v1").inc(2.0)
        _ <- counter.labels("v1", "v1").inc()
        _ <- counter.labels("v2", "v2").inc(1.0)
        v2 <- value("v1")
        v3 <- value("v2")
        v4 <- value("v3")
      } yield {
        v1 shouldEqual Some(0.0)
        v2 shouldEqual Some(3.0)
        v3 shouldEqual Some(1.0)
        v4 shouldEqual Some(0.0)
      }
    }
  }


  private def testSummary[F[_] : Sync] = {

    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val summary = registry.summary(
      name = "summary",
      help = "help_test",
      labels = LabelNames(),
      quantiles = Quantiles(Quantile(value = 0.5, error = 0.05)))

    summary.mapK(FunctionK.id[F]).use { summary =>
      for {
        _ <- summary.observe(1.0)
        _ <- summary.observe(2.0)
        sum <- registryP.value[F]("summary_sum")
        count <- registryP.value[F]("summary_count")
      } yield {
        sum shouldEqual 3.0
        count shouldEqual 2.0
      }
    }
  }


  private def testSummaryWithInitialLabels[F[_] : Sync] = {

    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val summary = registry.summaryWithInitialLabels(
      name = "summary",
      help = "help_test",
      labels = LabelNames("l1", "l2"),
      quantiles = Quantiles(Quantile(value = 0.5, error = 0.05))
    ).labelValues(Nel.of("v1", "v2"), Nel.of("v1", "v2"))

    def value(metricName: String, value: String) = {
      registryP.value[F](metricName, Nel.of("l1", "l2"), Nel.of(value, value))
    }

    summary.mapK(FunctionK.id[F]).use { summary =>
      for {
        _ <- summary.labels("v1", "v1").observe(1.0)
        _ <- summary.labels("v1", "v1").observe(2.0)
        sum <- value("summary_sum", "v1")
        count <- value("summary_count", "v1")
        sum1 <- value("summary_sum", "v2")
        count1 <- value("summary_sum", "v2")
        sum2 <- value("summary_sum", "v3")
        count2 <- value("summary_sum", "v3")
      } yield {
        sum shouldEqual Some(3.0)
        count shouldEqual Some(2.0)
        sum1 shouldEqual Some(0.0)
        count1 shouldEqual Some(0.0)
        sum2 shouldEqual None
        count2 shouldEqual None
      }
    }
  }


  private def testHistogram[F[_] : Sync] = {
    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val histogram = registry.histogram(
      name = "histogram",
      help = "help_test",
      labels = LabelNames("l1", "l2", "l3"),
      buckets = Buckets.linear(1.0, 1.0, 3))

    def value(value: String) = {
      registryP.value[F](value, Nel.of("l1", "l2", "l3"), Nel.of("n1", "n2", "n3"))
    }

    histogram.mapK(FunctionK.id[F]).use { histogram =>
      for {
        _ <- histogram.labels("n1", "n2", "n3").observe(1.0)
        _ <- histogram.labels("n1", "n2", "n3").observe(2.0)
        sum <- value("histogram_sum")
        count <- value("histogram_count")
      } yield {
        sum shouldEqual Some(3.0)
        count shouldEqual Some(2.0)
      }
    }
  }


  private def testHistogramWithInitialLabels[F[_] : Sync] = {
    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val histogram = registry.histogramWithInitialLabels(
      name = "histogram",
      help = "help_test",
      labels = LabelNames("l1", "l2", "l3"),
      buckets = Buckets.linear(1.0, 1.0, 3)
    ).labelValues(Nel.of("n1"), Nel.of("n2"), Nel.of("n3"))

    def value(value: String) = {
      registryP.value[F](value, Nel.of("l1", "l2", "l3"), Nel.of("n1", "n2", "n3"))
    }

    histogram.mapK(FunctionK.id[F]).use { histogram =>
      for {
        _ <- histogram.labels("n1", "n2", "n3").observe(1.0)
        _ <- histogram.labels("n1", "n2", "n3").observe(2.0)
        sum <- value("histogram_sum")
        count <- value("histogram_count")
      } yield {
        sum shouldEqual Some(3.0)
        count shouldEqual Some(2.0)
      }
    }
  }
}

object CollectorRegistryPrometheusSpec {

  implicit class CollectorRegistryOps(val self: P.CollectorRegistry) extends AnyVal {

    def value[F[_] : Sync](metric: String, names: Nel[String], values: Nel[String]): F[Option[Double]] = {
      Sync[F].delay {
        Option(self.getSampleValue(metric, names.toList.toArray, values.toList.toArray): java.lang.Double)
          .map(_.toDouble)
      }
    }

    def value[F[_] : Sync](metric: String): F[Double] = {
      Sync[F].delay { self.getSampleValue(metric) }
    }
  }
}