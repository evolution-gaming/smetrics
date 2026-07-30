package com.evolutiongaming.smetrics

import cats.effect.{Async, Resource, Sync, Temporal}
import cats.syntax.all.*

import java.lang.management.ManagementFactory
import scala.concurrent.duration.*

/**
 * Periodically gauges the number of live threads of every thread pool.
 *
 * The JVM exposes no pool membership for a thread, so threads are attributed to pools by their
 * name, using a caller-supplied [[MeterThreads.PoolNameOf]]. Threads it does not recognise are not
 * reported at all.
 */
object MeterThreads {

  type ThreadName = String

  /**
   * Name of the pool a thread belongs to, or `None` for a thread which should not be reported.
   *
   * Return a bounded set of names. Every name returned once is gauged on every sample from then on,
   * as a label value of its own, so names derived from per-connection or per-tenant thread names
   * grow the metric without bound.
   */
  type PoolNameOf = ThreadName => Option[String]

  val DefaultInterval: FiniteDuration = 1.minute

  /**
   * Gauges the threads of the running JVM, see [[threads]], every [[DefaultInterval]].
   *
   * Compose [[Metrics.make]] with the overload below to gauge under a different prefix, at a
   * different interval, or a set of threads other than the ones of the running JVM.
   */
  def make[F[_]: Async](
    collectorRegistry: CollectorRegistry[F],
    poolNameOf: PoolNameOf,
  ): Resource[F, Unit] = {
    for {
      metrics <- Metrics.make[F](collectorRegistry)
      result <- make(metrics, poolNameOf, threads[F])
    } yield result
  }

  /**
   * Gauges the threads reported by `threads`, re-sampling them every `interval`.
   *
   * The first sample is taken after `interval` has elapsed, so that pools created while the
   * application is still starting up are not gauged as empty.
   *
   * Sampling runs in a background fiber, cancelled on release. A sample which fails, say because
   * `poolNameOf` threw on a thread name it could not parse, is skipped silently and sampling
   * carries on at the next interval, rather than leaving the gauges frozen for good.
   */
  def make[F[_]: Temporal](
    metrics: Metrics[F],
    poolNameOf: PoolNameOf,
    threads: F[List[ThreadName]],
    interval: FiniteDuration = DefaultInterval,
  ): Resource[F, Unit] = {

    def threadCountByPool = {
      for {
        threadNames <- threads
      } yield {
        threadNames
          .flatMap { threadName => poolNameOf(threadName) }
          .groupMapReduce(identity)(_ => 1)(_ + _)
      }
    }

    def observe(poolNames: Set[String], threadCounts: Map[String, Int]) = {
      poolNames.toList.traverse_ { poolName =>
        metrics.threads(poolName, threadCounts.getOrElse(poolName, 0))
      }
    }

    // pool names seen so far are carried over, so that a pool which lost all of its threads is gauged as zero
    // instead of being left at the value it was last seen with
    def sample(poolNames: Set[String]) = {
      for {
        threadCounts <- threadCountByPool
        poolNamesSeen = poolNames ++ threadCounts.keySet
        _ <- observe(poolNamesSeen, threadCounts)
      } yield poolNamesSeen
    }

    val process = Set.empty[String].tailRecM { poolNames =>
      for {
        _ <- Temporal[F].sleep(interval)
        sampled <- sample(poolNames).attempt
      } yield {
        sampled.getOrElse(poolNames).asLeft[Unit]
      }
    }

    Temporal[F].background(process).void
  }

  /**
   * Names of all live threads of the running JVM.
   */
  def threads[F[_]: Sync]: F[List[ThreadName]] = {
    Sync[F].blocking {
      val threadBean = ManagementFactory.getThreadMXBean
      for {
        threadInfo <- threadBean.getThreadInfo(threadBean.getAllThreadIds).toList
        threadName <- Option(threadInfo).flatMap { thread => Option(thread.getThreadName) }.toList
      } yield threadName
    }
  }

  trait Metrics[F[_]] {

    def threads(poolName: String, threads: Int): F[Unit]
  }

  object Metrics {

    type Prefix = String

    object Prefix {

      /**
       * Kept as `dispatcher` for backwards compatibility: the resulting `dispatcher_threads` gauge
       * is the one existing dashboards and alerts are built on.
       */
      val Default: Prefix = "dispatcher"
    }

    def make[F[_]](
      registry: CollectorRegistry[F],
      prefix: Prefix = Prefix.Default,
    ): Resource[F, Metrics[F]] = {
      for {
        threadsGauge <- registry.gauge(s"${ prefix }_threads", "Number of threads in pool", LabelNames("poolName"))
      } yield {
        new Metrics[F] {
          def threads(poolName: String, threads: Int): F[Unit] = {
            threadsGauge.labels(poolName).set(threads.toDouble)
          }
        }
      }
    }
  }
}
