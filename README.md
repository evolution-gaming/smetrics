# SMetrics

[![Build Status](https://github.com/evolution-gaming/smetrics/workflows/CI/badge.svg)](https://github.com/evolution-gaming/smetrics/actions?query=workflow%3ACI)
[![Coverage Status](https://coveralls.io/repos/evolution-gaming/smetrics/badge.svg)](https://coveralls.io/r/evolution-gaming/smetrics)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/fbd49a562cc049028bf97ddb34b34103)](https://app.codacy.com/gh/evolution-gaming/smetrics/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Version](https://img.shields.io/badge/version-click-blue)](https://evolution.jfrog.io/artifactory/api/search/latestVersion?g=com.evolutiongaming&a=smetrics_2.13&repos=public)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellowgreen.svg)](https://opensource.org/licenses/MIT)

## Features:
* tagless final api via [cats](https://typelevel.org/cats/)
* improved abstraction for matching labels names and label values
* resource safety
* detaching metrics backend - prometheus dependency is one of the backend implementations

## Example of http metrics

let's declare interface for metrics, that seems reasonable for http client 

```scala

trait HttpMetrics[F[_]] {

  def latency(client: String, method: String, resource: String, duration: FiniteDuration): F[Unit]

  def count(client: String, method: String, resource: String, success: Boolean): F[Unit]

  def enqueue(client: String): F[Unit]

  def dequeue(client: String): F[Unit]
}
```

now let's implement `HttpMetrics` using `CollectorRegistry`

```scala
  import com.evolutiongaming.smetrics.MetricsHelper.*
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

## Kafka module
`smetrics-prometheus-kafka` module allows defining Prometheus collectors to obtain Kafka client's internal metrics
(ones collected by a producer/consumer themselves).
### `KafkaMetricsCollector`
See the example:
```scala
val collectorRegistry: CollectorRegistry = ???
val consumer: Consumer[IO, String, String] = ???
val collector = new KafkaMetricsCollector[IO](consumer.clientMetrics)
collectorRegistry.register(collector)
```
In the example above `KafkaMetricsCollector` will call `consumer.clientMetrics` each time the registry attempts to
collect metric samples

#### Multiple clients in the same VM
Creating multiple instances of `KafkaMetricsCollector` for different producers or consumers and attempting
to register them in `CollectorRegistry` will cause an error as they will contain metrics with the same name
which is prohibited by `CollectorRegistry`. There are multiple ways to mitigate the issue:
1. Using prefixes  
`KafkaMetricsCollector` allows passing an optional prefix which can be prepended to all metrics' names. 
This will result in multiple sets of metrics, e.g. `{prefix_1}_producer_metrics_request_size_avg` and 
`{prefix_2}_producer_metrics_request_size_avg` 
2. Combining the output of `clientMetrics` methods  
See the example below:
```scala
val collectorRegistry: CollectorRegistry = ???
val consumer1: Consumer[IO, String, String] = ???
val consumer2: Consumer[IO, String, String] = ???
val getAllMetrics: IO[Seq[ClientMetric[IO]]] = for {
  metrics1 <- consumer1.clientMetrics
  metrics2 <- consumer2.clientMetrics
} yield metrics1 ++ metrics2
val collector = new KafkaMetricsCollector[IO](getAllMetrics)
collectorRegistry.register(collector)
```
Note that this approach will result in duplicate metrics in case clients have the same configuration, e.g. when two
consumers have the same `client.id`:
```
consumer_metrics_connection_creation_rate{client_id="client1",} 0.0
consumer_metrics_connection_creation_rate{client_id="client1",} 0.0
```
3. Using `KafkaMetricsRegistry` described below.

### `KafkaMetricsRegistry`
`KafkaMetricsRegistry` is an abstraction which aims to simplify gathering metrics from multiple clients in the same VM.
It allows 'registering' functions obtaining metrics from different clients, aggregating them into a single list
of metrics when collected. This allows defining clients in different code units with the only requirement of registering
them in `KafkaMetricsRegistry`. The registered functions will be saved in a `Ref` and invoked every time metrics 
are collected.   
Please note that `KafkaMetricsRegistry` doesn't extend Prometheus' `Collector`, thus it's still
necessary to create a single instance of `KafkaMetricsCollector` and register it with `CollectorRegistry`.  
There are two ways of using `KafkaMetricsRegistry`:
1. Manual registration of each client
```scala
val collectorRegistry: CollectorRegistry = ???
val consumerOf: ConsumerOf[F] = ???
for {
  kafkaRegistry  <- KafkaMetricsRegistry.ref[IO].toResource
  // Manually register each client after creating
  consumer1      <- consumerOf.apply[K, V](config)
  _              <- kafkaRegistry.register(consumer1.clientMetrics)
  consumer2      <- consumerOf.apply[K, V](config)
  _              <- kafkaRegistry.register(consumer2.clientMetrics)
  // Create and register a single collector
  kafkaCollector = new KafkaMetricsCollector[F](kafkaRegistry.collectAll)
  _              <- F.delay(prometheusRegistry.register(kafkaCollector)).toResource
} yield ()
```
2. Wrapping `ConsumerOf` or `ProducerOf` with a syntax extension
```scala
import com.evolutiongaming.smetrics.kafka.syntax._

val collectorRegistry: CollectorRegistry = ???
val consumerConfig1 =
  ConsumerConfig.Default.copy(
    groupId = Some("group1"), 
    common = CommonConfig.Default.copy(clientId = Some("client1"))
  )

val consumerConfig2 =
  ConsumerConfig.Default.copy(
    groupId = Some("group2"), 
    common = CommonConfig.Default.copy(clientId = Some("client2"))
  )

for {
  kafkaRegistry  <- KafkaMetricsRegistry.ref[F].toResource
  // All consumers created with this factory will automatically register their metrics functions to `kafkaRegistry`
  consumerOf     = ConsumerOf.apply1[F]().withNativeMetrics(kafkaRegistry)
  consumer1      <- consumerOf.apply[String, String](consumerConfig1)
  consumer2      <- consumerOf.apply[String, String](consumerConfig2)
  // Create and register a single collector
  kafkaCollector = new KafkaMetricsCollector[F](kafkaRegistry.collectAll)
  _              <- F.delay(prometheusRegistry.register(kafkaCollector)).toResource
} yield ()
```
#### Metrics duplication
`KafkaMetricsRegistry` deduplicates metrics by default. It can be turned off by using a different factory method
accepting `allowDuplicates` parameter.
When using it in the default mode it's important to use different `client.id` values for different clients inside a 
single VM, otherwise only one of them will be picked (order is not guaranteed). 

## Setup

```scala
addSbtPlugin("com.evolution" % "sbt-artifactory-plugin" % "0.0.2")

libraryDependencies += "com.evolutiongaming" %% "smetrics" % "0.3.1"

libraryDependencies += "com.evolutiongaming" %% "smetrics-prometheus" % "0.3.1"
```

For prometheus version 1.x.x+ use `prometheus_v1` module:

```scala
libraryDependencies += "com.evolutiongaming" %% "smetrics-prometheus-v1" % "0.3.1"
```

For sttp3 use `smetrics-sttp3` module:

```scala
libraryDependencies += "com.evolutiongaming" %% "smetrics-sttp3" % "x.y.z"
```
