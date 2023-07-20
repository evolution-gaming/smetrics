package com.evolutiongaming.smetrics

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.implicits.effectResourceOps
import cats.syntax.all._
import com.evolutiongaming.catshelper.SerialRef
import com.evolutiongaming.smetrics.CollectorRegistry.CachedRegistryException
import io.prometheus.client.{CollectorRegistry => JavaRegistry}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CollectorRegistryCachedSpec extends AnyWordSpec with Matchers {

  "cached collector registry" should {

    import cats.effect.unsafe.implicits.global

    "create multiple gauges with same name" in {
      val res = for {
        pr <- Prometheus.cached[IO](JavaRegistry.defaultRegistry).toResource
        g1 <- pr.registry.gauge("foo", "bar", LabelNames("baz"))
        g2 <- pr.registry.gauge("foo", "bar", LabelNames("baz"))
      } yield (pr, g1, g2)
      res
        .use {
          case (pr, g1, g2) =>
            for {
              _ <- g1.labels("foo").inc()
              _ <- g2.labels("bar").inc()
              r <- pr.write004
            } yield
              r shouldBe
                """# HELP foo bar
                  |# TYPE foo gauge
                  |foo{baz="bar",} 1.0
                  |foo{baz="foo",} 1.0
                  |""".stripMargin
        }
        .unsafeRunSync()
    }

    "create multiple gauges with same name but different labels" in {
      val res = for {
        p <- Prometheus.cached[IO](JavaRegistry.defaultRegistry).toResource
        _ <- p.registry.gauge("foo", "bar", LabelNames("baz"))
        _ <- p.registry.gauge("foo", "bar", LabelNames("ams"))
      } yield {}
      try {
        res.use_.unsafeRunSync()
        fail("the test must throw CachedRegistryException")
      } catch {
        case e: CachedRegistryException =>
          e.getMessage shouldBe "metric `foo` of type `gauge` with labels [baz] already registered, while new metric tried to be created with labels [ams]"
      }
    }

    "create gauge and then counter with same name" in {
      val res = for {
        p <- Prometheus.cached[IO](JavaRegistry.defaultRegistry).toResource
        _ <- p.registry.gauge("foo", "bar", LabelNames("baz"))
        _ <- p.registry.counter("foo", "bar", LabelNames("ams"))
      } yield {}
      try {
        res.use_.unsafeRunSync()
        fail("the test must throw CachedRegistryException")
      } catch {
        case e: CachedRegistryException =>
          e.getMessage shouldBe "metric `foo` of type `gauge` with labels [baz] already registered, while new metric of type `counter` tried to be created"
      }
    }

    "create gauge and release it " in {

      val prometheus = Prometheus.default[IO]

      val io = for {
        ref <- SerialRef
          .of[IO, Map[String, CollectorRegistry.Cached.Entry]](Map.empty)
        _ <- new CollectorRegistry.Cached(prometheus.registry, ref)
          .counter("foo", "bar", LabelNames("baz"))
          .use { _ =>
            for {
              c <- ref.get
            } yield c.contains("foo") shouldBe true
          }
        c <- ref.get
      } yield c.get("foo") shouldBe none

      io.unsafeRunSync()
    }

    "create multiple counters with same name" in {
      val res = for {
        pr <- Prometheus.cached[IO](JavaRegistry.defaultRegistry).toResource
        c1 <- pr.registry.counter("foo", "bar", LabelNames("baz"))
        c2 <- pr.registry.counter("foo", "bar", LabelNames("baz"))
      } yield (pr, c1, c2)
      res
        .use {
          case (pr, c1, c2) =>
            for {
              _ <- c1.labels("foo").inc()
              _ <- c2.labels("bar").inc()
              r <- pr.write004
            } yield
              r shouldBe
                """# HELP foo bar
                   |# TYPE foo counter
                   |foo{baz="bar",} 1.0
                   |foo{baz="foo",} 1.0
                   |""".stripMargin
        }
        .unsafeRunSync()
    }

    "create multiple summaries with same name" in {
      val res = for {
        pr <- Prometheus.cached[IO](JavaRegistry.defaultRegistry).toResource
        s1 <- pr.registry.summary(
          "foo",
          "bar",
          Quantiles.Empty,
          LabelNames("baz")
        )
        s2 <- pr.registry.summary(
          "foo",
          "bar",
          Quantiles.Empty,
          LabelNames("baz")
        )
      } yield (pr, s1, s2)
      res
        .use {
          case (pr, s1, s2) =>
            for {
              _ <- s1.labels("foo").observe(42d)
              _ <- s2.labels("bar").observe(42d)
              r <- pr.write004
            } yield
              r shouldBe
                """# HELP foo bar
                  |# TYPE foo summary
                  |foo_count{baz="bar",} 1.0
                  |foo_sum{baz="bar",} 42.0
                  |foo_count{baz="foo",} 1.0
                  |foo_sum{baz="foo",} 42.0
                  |""".stripMargin
        }
        .unsafeRunSync()
    }

    "create multiple histograms with same name" in {
      val res = for {
        pr <- Prometheus.cached[IO](JavaRegistry.defaultRegistry).toResource
        h1 <- pr.registry.histogram(
          "foo",
          "bar",
          Buckets(NonEmptyList.one(42d)),
          LabelNames("baz")
        )
        h2 <- pr.registry.histogram(
          "foo",
          "bar",
          Buckets(NonEmptyList.one(42d)),
          LabelNames("baz")
        )
      } yield (pr, h1, h2)
      res
        .use {
          case (pr, h1, h2) =>
            for {
              _ <- h1.labels("foo").observe(42d)
              _ <- h2.labels("bar").observe(42d)
              r <- pr.write004
            } yield
              r shouldBe
                """# HELP foo bar
                  |# TYPE foo histogram
                  |foo_bucket{baz="bar",le="42.0",} 1.0
                  |foo_bucket{baz="bar",le="+Inf",} 1.0
                  |foo_count{baz="bar",} 1.0
                  |foo_sum{baz="bar",} 42.0
                  |foo_bucket{baz="foo",le="42.0",} 1.0
                  |foo_bucket{baz="foo",le="+Inf",} 1.0
                  |foo_count{baz="foo",} 1.0
                  |foo_sum{baz="foo",} 42.0
                  |""".stripMargin
        }
        .unsafeRunSync()
    }

  }

}
