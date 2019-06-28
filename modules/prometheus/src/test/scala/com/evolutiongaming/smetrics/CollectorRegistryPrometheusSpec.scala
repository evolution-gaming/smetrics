package com.evolutiongaming.smetrics

import cats.data.{NonEmptyList => Nel}
import cats.effect.{IO, Sync}
import cats.implicits._
import com.evolutiongaming.smetrics.IOSuite._
import io.prometheus.{client => P}
import org.scalatest.{AsyncFunSuite, Matchers}

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

    gauge.use { gauge =>
      for {
        _  <- gauge.labels("v1").set(2.0)
        _  <- gauge.labels("v2").inc(2.0)
        _  <- gauge.labels("v2").dec(1.0)
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
    val registry = CollectorRegistryPrometheus[F](registryP)

    val counter = registry.counter(
      name = "counter",
      help = "help_test",
      labels = LabelNames("l1", "l2"))

    def value(value: String) = {
      registryP.value[F]("counter", Nel.of("l1", "l2"), Nel.of(value, value))
    }

    counter.use { counter =>
      for {
        _  <- counter.labels("v1", "v1").inc(2.0)
        _  <- counter.labels("v1", "v1").inc()
        _  <- counter.labels("v2", "v2").inc(1.0)
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
    val registry = CollectorRegistryPrometheus[F](registryP)

    val counter = registry.summary(
      name = "summary",
      help = "help_test",
      labels = LabelNames(),
      quantiles = Quantiles(Quantile(value = 0.5, error = 0.05)))

    counter.use { counter =>
      for {
        _     <- counter.observe(1.0)
        _     <- counter.observe(2.0)
        sum   <- registryP.value[F]("summary_sum")
        count <- registryP.value[F]("summary_count")
      } yield {
        sum shouldEqual 3.0
        count shouldEqual 2.0
      }
    }
  }
}

object CollectorRegistryPrometheusSpec {

  implicit class CollectorRegistryOps(val self: P.CollectorRegistry) extends AnyVal {

    def value[F[_] : Sync](metric: String, names: Nel[String], values: Nel[String]): F[Double] = {
      Sync[F].delay { self.getSampleValue(metric, names.toList.toArray, values.toList.toArray) }
    }

    def value[F[_] : Sync](metric: String): F[Double] = {
      Sync[F].delay { self.getSampleValue(metric) }
    }
  }
}
