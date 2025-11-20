package com.evolutiongaming.smetrics

import cats.data.NonEmptyList
import cats.effect.{Deferred, IO}
import cats.syntax.all._
import com.evolutiongaming.catshelper.SerialRef
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CollectorRegistryCachedSpec extends AnyWordSpec with Matchers {

  "cached collector registry" should {

    import cats.effect.unsafe.implicits.global

    "create multiple gauges with same name" in {
      val res = for {
        pr <- Prometheus.default[IO].withCaching.toResource
        g1 <- pr.registry.gauge("foo", "bar", LabelNames("baz"))
        g2 <- pr.registry.gauge("foo", "bar", LabelNames("baz"))
      } yield (pr, g1, g2)
      res
        .use { case (pr, g1, g2) =>
          for {
            _ <- g1.labels("foo").inc()
            _ <- g2.labels("bar").inc()
            r <- pr.write004
          } yield r shouldBe
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
        p <- Prometheus.default[IO].withCaching.toResource
        _ <- p.registry.gauge("foo", "bar", LabelNames("baz"))
        _ <- p.registry.gauge("foo", "bar", LabelNames("ams"))
      } yield {}
      try {
        res.use_.unsafeRunSync()
        fail("the test must throw CachedRegistryException")
      } catch {
        case e: Exception =>
          e.getMessage shouldBe "metric `foo` of type `gauge` with labels [baz] already registered, while new metric tried to be created with labels [ams]"
      }
    }

    "create gauge and then counter with same name" in {
      val res = for {
        p <- Prometheus.default[IO].withCaching.toResource
        _ <- p.registry.gauge("foo", "bar", LabelNames("baz"))
        _ <- p.registry.counter("foo", "bar", LabelNames("ams"))
      } yield {}
      try {
        res.use_.unsafeRunSync()
        fail("the test must throw CachedRegistryException")
      } catch {
        case e: Exception =>
          e.getMessage shouldBe "metric `foo` of type `gauge` with labels [baz] already registered, while new metric of type `counter` tried to be created"
      }
    }

    "create counter and release it" in {

      val prometheus = Prometheus.default[IO]

      val io = for {
        ref <- SerialRef
                 .of[IO, Map[String, CollectorRegistry.Cached.Entry[IO]]](Map.empty)
        _   <- new CollectorRegistry.Cached(prometheus.registry, ref)
                 .counter("foo", "bar", LabelNames("baz"))
                 .use { _ =>
                   for {
                     c <- ref.get
                   } yield c.contains("foo") shouldBe true
                 }
        c   <- ref.get
      } yield c.get("foo") shouldBe none

      io.unsafeRunSync()
    }

    "create multiple counters and release first one, then use others" in {

      val prometheus = Prometheus.default[IO]

      val io = for {
        ref <- SerialRef
                 .of[IO, Map[String, CollectorRegistry.Cached.Entry[IO]]](Map.empty)
        rg   = new CollectorRegistry.Cached(prometheus.registry, ref)
        cr   = rg.counter("foo", "bar", LabelNames("baz"))

        d1 <- Deferred[IO, Unit]
        d2 <- Deferred[IO, Unit]

        t1  = for {
                c <- cr
                _ <- d1.complete({}).toResource
                _ <- d2.get.toResource
              } yield c
        f1 <- t1.use_.start

        t2  = d1.get.toResource >> cr
        r2 <- t2.allocated
        _  <- r2._1.labels("aaa").inc()
        _  <- d2.complete({})
        // resource t2 NOT finalized!

        o1 <- f1.join
        _  <- o1.embed(IO.unit)

        c0 <- ref.get
        _  <- r2._2
        c1 <- ref.get
      } yield {
        c0.contains("foo") shouldBe true
        c1.contains("foo") shouldBe false
      }

      io.unsafeRunSync()
    }

    "create multiple counters with same name" in {
      val res = for {
        pr <- Prometheus.default[IO].withCaching.toResource
        c1 <- pr.registry.counter("foo", "bar", LabelNames("baz"))
        c2 <- pr.registry.counter("foo", "bar", LabelNames("baz"))
      } yield (pr, c1, c2)
      res
        .use { case (pr, c1, c2) =>
          for {
            _ <- c1.labels("foo").inc()
            _ <- c2.labels("bar").inc()
            r <- pr.write004
          } yield r shouldBe
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
        pr <- Prometheus.default[IO].withCaching.toResource
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
        .use { case (pr, s1, s2) =>
          for {
            _ <- s1.labels("foo").observe(42d)
            _ <- s2.labels("bar").observe(42d)
            r <- pr.write004
          } yield r shouldBe
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
        pr <- Prometheus.default[IO].withCaching.toResource
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
        .use { case (pr, h1, h2) =>
          for {
            _ <- h1.labels("foo").observe(42d)
            _ <- h2.labels("bar").observe(42d)
            r <- pr.write004
          } yield r shouldBe
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
