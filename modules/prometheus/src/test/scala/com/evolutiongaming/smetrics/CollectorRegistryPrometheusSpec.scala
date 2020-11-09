package com.evolutiongaming.smetrics

import cats.arrow.FunctionK
import cats.data.{NonEmptyList => Nel}
import cats.effect.{IO, Resource, Sync}
import cats.implicits._
import com.evolutiongaming.smetrics.IOSuite._
import com.evolutiongaming.smetrics.LabelValues.`0`
import io.prometheus.{client => P}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class CollectorRegistryPrometheusSpec extends AsyncFunSuite with Matchers {

  import CollectorRegistryPrometheusSpec._

  test("gauge") {
    testGauge[IO].run()
  }

  test("counter") {
    testCounter[IO].run()
  }

  test("summary") {
    testSummary[IO].run()
  }

  test("histogram") {
    testHistogram[IO].run()
  }

  private def testGauge[F[_] : Sync] = {

    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val gauge = registry.gauge(
      name = "gauge",
      help = "help_test",
      labels = LabelNames("l1"))

    val initializedGauge = registry.gaugeInitialized(
      name = "gauge",
      help = "help_test",
      labels = LabelsInitialized()
        .add("l1", Nel.of("v1", "v2", "v3")))

    def value(value: String) = {
      registryP.value[F]("gauge", Nel.of("l1"), Nel.of(value))
    }

    def check(gauge: Resource[F, LabelValues.`1`[Gauge[F]]], defaultValue: Option[Double]) =
      gauge.mapK(FunctionK.id[F]).use { gauge =>
        for {
          v1 <- value("v1")
          _ <- gauge.labels("v1").set(2.0)
          _ <- gauge.labels("v2").inc(2.0)
          _ <- gauge.labels("v2").dec()
          v2 <- value("v1")
          v3 <- value("v2")
          v4 <- value("v3")
        } yield {
          v1 shouldEqual defaultValue
          v2 shouldEqual Some(2.0)
          v3 shouldEqual Some(1.0)
          v4 shouldEqual defaultValue
        }
      }

    check(gauge, None) *> check(initializedGauge, Some(0.0))
  }


  private def testCounter[F[_] : Sync] = {

    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val counter = registry.counter(
      name = "counter",
      help = "help_test",
      labels = LabelNames("l1", "l2"))

    val initializedCounter = registry.counterInitialized(
      name = "counter",
      help = "help_test",
      labels = LabelsInitialized()
        .add("l1", Nel.of("v1", "v2", "v3"))
        .add("l2", Nel.of("v1", "v2", "v3")))

    def value(value1: String, value2: String) = {
      registryP.value[F]("counter", Nel.of("l1", "l2"), Nel.of(value1, value2))
    }

    def check(counter: Resource[F, LabelValues.`2`[Counter[F]]], defaultValue: Option[Double]) =
      counter.mapK(FunctionK.id[F]).use { counter =>
        for {
          v1 <- value("v2", "v3")
          _ <- counter.labels("v3", "v1").inc(2.0)
          _ <- counter.labels("v3", "v1").inc()
          _ <- counter.labels("v2", "v2").inc()
          v2 <- value("v3", "v1")
          v3 <- value("v2", "v2")
          v4 <- value("v3", "v3")
        } yield {
          v1 shouldEqual defaultValue
          v2 shouldEqual Some(3.0)
          v3 shouldEqual Some(1.0)
          v4 shouldEqual defaultValue
        }
      }

    check(counter, None) *> check(initializedCounter, Some(0.0))
  }


  private def testSummary[F[_] : Sync] = {

    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val summary = registry.summary(
      name = "summary",
      help = "help_test",
      labels = LabelNames(),
      quantiles = Quantiles(Quantile(value = 0.5, error = 0.05)))

    val initializedSummary = registry.summaryInitialized(
      name = "summary",
      help = "help_test",
      labels = LabelsInitialized(),
      quantiles = Quantiles(Quantile(value = 0.5, error = 0.05)))

    def check(summary: Resource[F, `0`[Summary[F]]]) =
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

    check(summary) *> check(initializedSummary)
  }


  private def testHistogram[F[_] : Sync] = {
    val registryP = new P.CollectorRegistry()
    val registry = CollectorRegistryPrometheus[F](registryP)

    val histogram = registry.histogram(
      name = "histogram",
      help = "help_test",
      labels = LabelNames("l1", "l2", "l3"),
      buckets = Buckets.linear(1.0, 1.0, 3))

    val initializedHistogram = registry.histogramInitialized(
      name = "histogram",
      help = "help_test",
      labels = LabelsInitialized()
        .add("l1", Nel.of("n1", "n2", "n3"))
        .add("l2", Nel.of("n1", "n2", "n3"))
        .add("l3", Nel.of("n1", "n2", "n3")),
      buckets = Buckets.linear(1.0, 1.0, 3))

    def value(metricName: String, value: String) = {
      registryP.value[F](metricName, Nel.of("l1", "l2", "l3"), Nel.of(value, value, value))
    }

    def check(histogram: Resource[F, LabelValues.`3`[Histogram[F]]], defaultValue: Option[Double]) =
      histogram.mapK(FunctionK.id[F]).use { histogram =>
        for {
          _ <- histogram.labels("n1", "n1", "n1").observe(1.0)
          _ <- histogram.labels("n1", "n1", "n1").observe(2.0)
          sum1 <- value("histogram_sum", "n1")
          count1 <- value("histogram_count", "n1")
          sum2 <- value("histogram_sum", "n2")
          count2 <- value("histogram_count", "n2")
          sum3 <- value("histogram_sum", "n3")
          count3 <- value("histogram_count", "n3")
        } yield {
          sum1 shouldEqual Some(3.0)
          count1 shouldEqual Some(2.0)
          sum2 shouldEqual defaultValue
          count2 shouldEqual defaultValue
          sum3 shouldEqual defaultValue
          count3 shouldEqual defaultValue
        }
      }

    check(histogram, None) *> check(initializedHistogram, Some(0.0))
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