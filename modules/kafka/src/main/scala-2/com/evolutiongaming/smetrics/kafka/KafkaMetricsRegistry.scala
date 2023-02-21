package com.evolutiongaming.smetrics.kafka

import cats.effect.kernel.Sync
import cats.effect.{Ref, Resource}
import cats.syntax.all._
import com.evolutiongaming.skafka.ClientMetric

import java.util.UUID

/** Allows reporting metrics of multiple Kafka clients inside a single VM.
 * Note that it's still necessary to create an accompanying instance of `KafkaMetricsCollector` and register it
 * with Prometheus' collector.
 *
 * Example:
 * {{{
 *   val prometheusRegistry: CollectorRegistry = ...
 *   val kafkaRegistry: KafkaMetricsRegistry[F] = ...
 *   val kafkaCollector = new KafkaMetricsCollector[F](kafkaRegistry.collectAll)
 *   val consumerOf: ConsumerOf[F] = ...
 *
 *   for {
 *     _        <- F.delay(prometheusRegistry.register(kafkaCollector)).toResource
 *     consumer <- consumerOf.apply(config)
 *     _        <- kafkaRegistry.register(consumer.clientMetrics)
 *   } yield ...
 * }}}
 *
 * To avoid manually registering each client there are syntax extension, wrapping `ProducerOf` and `ConsumerOf`,
 * see `com.evolutiongaming.smetrics.kafka.syntax`.
 *
 * Example:
 * {{{
 *   import com.evolutiongaming.smetrics.kafka.syntax._
 *
 *   val prometheusRegistry: CollectorRegistry = ...
 *   val kafkaRegistry: KafkaMetricsRegistry[F] = ...
 *   val consumerOf = ConsumerOf.apply1[F]().withNativeMetrics(kafkaRegistry)
 *   val kafkaCollector = new KafkaMetricsCollector[F](kafkaRegistry.collectAll)
 *
 *   for {
 *     _ <- F.delay(prometheusRegistry.register(kafkaCollector)).toResource
 *     // usage of `consumerOf` as usual ...
 *   } yield ...
 * }}}
 *
 * */
trait KafkaMetricsRegistry[F[_]] {
  /**
   * Register a function to obtain a list of client metrics.
   * Normally, you would pass [[com.evolutiongaming.skafka.consumer.Consumer.clientMetrics]] or
   * [[com.evolutiongaming.skafka.producer.Producer.clientMetrics]]
   *
   * @return synthetic ID of registered function
   */
  def register(metrics: F[Seq[ClientMetric[F]]]): Resource[F, UUID]

  /** Collect metrics from all registered functions */
  def collectAll: F[Seq[ClientMetric[F]]]
}

object KafkaMetricsRegistry {
  private final class FromRef[F[_] : Sync](ref: Ref[F, Map[UUID, F[Seq[ClientMetric[F]]]]], allowDuplicates: Boolean)
    extends KafkaMetricsRegistry[F] {
    override def register(metrics: F[Seq[ClientMetric[F]]]): Resource[F, UUID] = {
      val acquire: F[UUID] = for {
        id <- Sync[F].delay(UUID.randomUUID())
        _ <- ref.update(m => m + (id -> metrics))
      } yield id

      def release(id: UUID): F[Unit] =
        ref.update(m => m - id)

      Resource.make(acquire)(id => release(id))
    }

    override def collectAll: F[Seq[ClientMetric[F]]] =
      ref.get.flatMap { map: Map[UUID, F[Seq[ClientMetric[F]]]] =>
        map.values.toList.sequence.map { metrics =>
          val results: List[ClientMetric[F]] = metrics.flatten

          if (allowDuplicates) {
            results
          } else {
            results
              .groupBy(metric => (metric.name, metric.group, metric.tags))
              .view
              .mapValues(metrics => metrics.head)
              .values
              .toSeq
          }
        }
      }
  }

  def ref[F[_] : Sync](allowDuplicates: Boolean): F[KafkaMetricsRegistry[F]] = {
    Ref.of[F, Map[UUID, F[Seq[ClientMetric[F]]]]](Map.empty).map(ref => new FromRef[F](ref, allowDuplicates))
  }

  def ref[F[_] : Sync]: F[KafkaMetricsRegistry[F]] = ref[F](allowDuplicates = false)
}
