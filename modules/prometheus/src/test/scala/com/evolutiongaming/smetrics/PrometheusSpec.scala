package com.evolutiongaming.smetrics

import cats.arrow.FunctionK
import cats.data.{NonEmptyList => Nel}
import cats.effect.{IO, Sync}
import cats.implicits._
import com.evolutiongaming.smetrics.IOSuite._
import io.prometheus.{client => P}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class PrometheusSpec extends AsyncFunSuite with Matchers {

  import PrometheusSpec._

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
    val registry = Prometheus[F](registryP).registry

    val gauge = registry.gauge(
      name = "gauge",
      help = "help_test",
      labels = LabelNames("l1"))

    def value(value: String) = {
      registryP.value[F]("gauge", Nel.of("l1"), Nel.of(value))
    }

    gauge.mapK(FunctionK.id).use { gauge =>
      for {
        _ <- gauge.labels("v1").set(2.0)
        _ <- gauge.labels("v2").inc(2.0)
        _ <- gauge.labels("v2").dec(1.0)
        v1 <- value("v1")
        v2 <- value("v2")
        v3 <- value("v3")
      } yield {
        v1 shouldEqual 2.0
        v2 shouldEqual 1.0
        v3 shouldEqual 0.0
      }
    }
  }


  private def testCounter[F[_] : Sync] = {

    val registryP = new P.CollectorRegistry()
    val registry = Prometheus[F](registryP).registry

    val counter = registry.counter(
      name = "counter",
      help = "help_test",
      labels = LabelNames("l1", "l2"))

    def value(value: String) = {
      registryP.value[F]("counter", Nel.of("l1", "l2"), Nel.of(value, value))
    }

    counter.mapK(FunctionK.id).use { counter =>
      for {
        _ <- counter.labels("v1", "v1").inc(2.0)
        _ <- counter.labels("v1", "v1").inc()
        _ <- counter.labels("v2", "v2").inc(1.0)
        v1 <- value("v1")
        v2 <- value("v2")
        v3 <- value("v3")
      } yield {
        v1 shouldEqual 3.0
        v2 shouldEqual 1.0
        v3 shouldEqual 0.0
      }
    }
  }


  private def testSummary[F[_] : Sync] = {

    val registryP = new P.CollectorRegistry()
    val registry = Prometheus[F](registryP).registry

    val summary = registry.summary(
      name = "summary",
      help = "help_test",
      labels = LabelNames(),
      quantiles = Quantiles(Quantile(value = 0.5, error = 0.05)))

    summary.mapK(FunctionK.id).use { summary =>
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


  private def testHistogram[F[_] : Sync] = {
    val registryP = new P.CollectorRegistry()
    val registry = Prometheus[F](registryP).registry

    val histogram = registry.histogram(
      name = "histogram",
      help = "help_test",
      labels = LabelNames("l1", "l2", "l3"),
      buckets = Buckets.linear(1.0, 1.0, 3))

    def value(value: String) = {
      registryP.value[F](value, Nel.of("l1", "l2", "l3"), Nel.of("n1", "n2", "n3"))
    }

    histogram.mapK(FunctionK.id).use { histogram =>
      for {
        _ <- histogram.labels("n1", "n2", "n3").observe(1.0)
        _ <- histogram.labels("n1", "n2", "n3").observe(2.0)
        sum <- value("histogram_sum")
        count <- value("histogram_count")
      } yield {
        sum shouldEqual 3.0
        count shouldEqual 2.0
      }
    }
  }
}

object PrometheusSpec {

  implicit class CollectorRegistryOps(val self: P.CollectorRegistry) extends AnyVal {

    def value[F[_] : Sync](metric: String, names: Nel[String], values: Nel[String]): F[Double] = {
      Sync[F].delay { self.getSampleValue(metric, names.toList.toArray, values.toList.toArray) }
    }

    def value[F[_] : Sync](metric: String): F[Double] = {
      Sync[F].delay { self.getSampleValue(metric) }
    }
  }
}
