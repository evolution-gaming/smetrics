# SMetrics

[![Build Status](https://github.com/evolution-gaming/smetrics/workflows/CI/badge.svg)](https://github.com/evolution-gaming/smetrics/actions?query=workflow%3ACI)
[![Coverage Status](https://coveralls.io/repos/evolution-gaming/smetrics/badge.svg)](https://coveralls.io/r/evolution-gaming/smetrics)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/04fb0fd38072413cb032d8a5e7c9def5)](https://www.codacy.com/app/evolution-gaming/smetrics?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=evolution-gaming/smetrics&amp;utm_campaign=Badge_Grade)
[![Version](https://img.shields.io/badge/version-click-blue)](https://evolution.jfrog.io/artifactory/api/search/latestVersion?g=com.evolutiongaming&a=smetrics_2.13&repos=public)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellowgreen.svg)](https://opensource.org/licenses/MIT)


## Example of http metrics

let's declare interface for metrics, that seems reasonable for http client 

```scala

trait HttpMetrics[F[_]] {

  def latency(client: String, method: String, resource: String, duration: FiniteDuration): F[Unit]

  def count(client: String, method: String, resource: String): F[Unit]

  def enqueue(client: String): F[Unit]

  def dequeue(client: String): F[Unit]
}
```

now let's implement `HttpMetrics` using `CollectorRegistry`

```scala
  import com.evolutiongaming.smetrics.MetricsHelper._
  import com.evolutiongaming.smetrics.{CollectorRegistry, LabelNames, Quantile, Quantiles}

  def httpMetrics[F[_]: Monad](collectorRegistry: CollectorRegistry[F]): Resource[F, HttpMetrics[F]] = {
    for {
      latencySummary <- collectorRegistry.summary(
        "http_client_latency",
        "Latency of HTTP requests processing in seconds",
        Quantiles(
          Quantile(value = 0.5, error = 0.05),
          Quantile(value = 0.9, error = 0.05),
          Quantile(value = 0.99, error = 0.005)),
        LabelNames("client_name", "method", "resource"))
      
      resultCounter <- collectorRegistry.counter(
        "http_client_response_result",
        "Status of HTTP responses",
        LabelNames("client_name", "method", "resource", "result"))
      
      queueGauge <- collectorRegistry.gauge(
        "http_client_queue",
        "Queue of incoming http calls",
        LabelNames("client_name"))
    } yield {
      new HttpMetrics[F] {
    
        def latency(client: String, method: String, resource: String, duration: FiniteDuration) = {
          latencySummary
            .labels(client, method, resource)
            .observe(duration.toNanos.nanosToSeconds)
        }
    
        def count(client: String, method: String, resource: String, success: Boolean) = {
          resultCounter
            .labels(client, method, resource, if (success) "success" else "error")
            .inc()
        }
    
        def enqueue(client: String) = {
          queueGauge
            .labels(client)
            .inc()
        }
    
        def dequeue(client: String) = {
          queueGauge
            .labels(client)
            .dec()
        }
      }
    }
  }
```

So you can see, `httpMetrics` function requires instance of `CollectorRegistry`.
Right now there are two options available:
* `CollectorRegistry.empty` - often used in tests
* `CollectorRegistryPrometheus` - which will create `smetrics.CollectorRegistry` out of `io.prometheus.CollectorRegistry` using [prometheus/client_java](github.com/prometheus/client_java) under the hood

Return type is `Resource[F, _]` rather than `F[_]` because most often underlying metrics implementation upon a call registers your metrics with shared registry.
Hence `release` hook of `Resource` being used in order to de-register particular metrics from shared registry.

## Setup

```scala
addSbtPlugin("com.evolution" % "sbt-artifactory-plugin" % "0.0.2")

libraryDependencies += "com.evolutiongaming" %% "smetrics" % "0.3.1"

libraryDependencies += "com.evolutiongaming" %% "smetrics-prometheus" % "0.3.1"
``` 
