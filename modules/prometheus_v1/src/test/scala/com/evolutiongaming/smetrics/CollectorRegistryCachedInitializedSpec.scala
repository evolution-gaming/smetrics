package com.evolutiongaming.smetrics

import cats.data.NonEmptyList as Nel
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Caching a registry must not change initialization semantics: the `*Initialized` variants have to
 * pre-create the time series for every combination of label values, so that they are exposed on
 * scrape before the first observation.
 */
class CollectorRegistryCachedInitializedSpec extends AnyWordSpec with Matchers {

  import cats.effect.unsafe.implicits.global

  private val labels = LabelsInitialized("baz", Nel.of("v1", "v2"))

  "cached collector registry" should {

    "pre-create label values for gaugeInitialized" in {
      verifyCachingPreservesInitialization {
        _.gaugeInitialized("foo", "bar", labels)
      }
    }

    "pre-create label values for counterInitialized" in {
      verifyCachingPreservesInitialization {
        _.counterInitialized("foo", "bar", labels)
      }
    }

    "pre-create label values for summaryInitialized" in {
      verifyCachingPreservesInitialization {
        _.summaryInitialized("foo", "bar", Quantiles.Empty, labels)
      }
    }

    "pre-create label values for histogramInitialized" in {
      verifyCachingPreservesInitialization {
        _.histogramInitialized("foo", "bar", Buckets(Nel.one(42d)), labels)
      }
    }
  }

  private def verifyCachingPreservesInitialization(
    createMetric: CollectorRegistry[IO] => Resource[IO, Any],
  ): Assertion = {
    val expected = scrape(caching = false, createMetric)
    val actual = scrape(caching = true, createMetric)

    expected should include("""baz="v1"""")
    actual shouldBe expected
  }

  private def scrape(
    caching: Boolean,
    createMetric: CollectorRegistry[IO] => Resource[IO, Any],
  ): String = {
    val prometheus = Prometheus[IO](new PrometheusRegistry())
    val res = for {
      prometheus <- if (caching) prometheus.withCaching.toResource else prometheus.pure[IO].toResource
      _ <- createMetric(prometheus.registry)
    } yield prometheus
    res.use(_.write004).unsafeRunSync()
  }
}
