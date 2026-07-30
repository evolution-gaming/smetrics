package com.evolutiongaming.smetrics

import cats.effect.testkit.TestControl
import cats.effect.{IO, Ref, Resource}
import cats.syntax.all.*
import com.evolutiongaming.smetrics.IOSuite.*
import io.prometheus.client as P
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.CountDownLatch
import scala.concurrent.duration.*

class MeterThreadsSpec extends AsyncFunSuite with Matchers {

  import MeterThreadsSpec.*

  private val poolNameOf: MeterThreads.PoolNameOf = { threadName =>
    List("worker", "other").find { poolName => threadName.startsWith(s"$poolName-") }
  }

  test("gauges the thread count of every recognised pool") {
    val threadNames = List("worker-1", "worker-2", "other-1", "unrecognised-1")
    val program = for {
      metrics <- RecordingMetrics.of
      _ <- MeterThreads
        .make[IO](metrics, poolNameOf, IO.pure(threadNames))
        .use { _ => IO.sleep(MeterThreads.DefaultInterval + 30.seconds) }
      observations <- metrics.observations
    } yield observations

    TestControl
      .executeEmbed(program)
      .map { observations => observations.sorted shouldEqual List("other" -> 1, "worker" -> 2) }
      .run()
  }

  test("gauges zero for a pool which no longer has threads") {
    val program = for {
      metrics <- RecordingMetrics.of
      threadNames <- scriptedThreadNames(IO.pure(List("worker-1")), IO.pure(List.empty))
      _ <- MeterThreads
        .make[IO](metrics, poolNameOf, threadNames, 1.minute)
        .use { _ => IO.sleep(150.seconds) }
      observations <- metrics.observations
    } yield observations

    TestControl
      .executeEmbed(program)
      .map { observations => observations shouldEqual List("worker" -> 1, "worker" -> 0) }
      .run()
  }

  test("stops gauging once released") {
    val program = for {
      metrics <- RecordingMetrics.of
      _ <- MeterThreads
        .make[IO](metrics, poolNameOf, IO.pure(List("worker-1")), 1.minute)
        .use { _ => IO.sleep(90.seconds) }
      _ <- IO.sleep(5.minutes)
      observations <- metrics.observations
    } yield observations

    TestControl
      .executeEmbed(program)
      .map { observations => observations shouldEqual List("worker" -> 1) }
      .run()
  }

  test("carries on gauging after poolNameOf throws") {
    val throwsOnUnparseable: MeterThreads.PoolNameOf = { threadName =>
      if (threadName == "unparseable") throw new RuntimeException("cannot parse")
      else poolNameOf(threadName)
    }
    val program = for {
      metrics <- RecordingMetrics.of
      threadNames <- scriptedThreadNames(IO.pure(List("unparseable")), IO.pure(List("worker-1")))
      _ <- MeterThreads
        .make[IO](metrics, throwsOnUnparseable, threadNames, 1.minute)
        .use { _ => IO.sleep(150.seconds) }
      observations <- metrics.observations
    } yield observations

    TestControl
      .executeEmbed(program)
      .map { observations => observations shouldEqual List("worker" -> 1) }
      .run()
  }

  test("gauges into dispatcher_threads labelled by poolName") {
    val prometheus = Prometheus[IO](new P.CollectorRegistry())
    MeterThreads
      .Metrics
      .make[IO](prometheus.registry)
      .use { metrics =>
        for {
          _ <- metrics.threads("worker", 3)
          exported <- prometheus.write004
        } yield exported should include("""dispatcher_threads{poolName="worker",} 3.0""")
      }
      .run()
  }

  test("threads lists the live threads of the running JVM") {
    probeThread(ProbeThreadName)
      .use { _ => MeterThreads.threads[IO] }
      .map { threadNames => threadNames should contain(ProbeThreadName) }
      .run()
  }

  test("gauges the threads of the running JVM under a custom prefix") {
    val prometheus = Prometheus[IO](new P.CollectorRegistry())
    val singlePool: MeterThreads.PoolNameOf = { _ => Some("all") }
    val meterThreads = for {
      metrics <- MeterThreads.Metrics.make[IO](prometheus.registry, prefix = "custom")
      result <- MeterThreads.make[IO](metrics, singlePool, MeterThreads.threads[IO], 10.millis)
    } yield result

    meterThreads
      .use { _ => gaugedThreadCount(prometheus) }
      .map { threadCount => threadCount should be > 0.0 }
      .run()
  }
}

object MeterThreadsSpec {

  private val ProbeThreadName = "meter-threads-spec-probe"

  private val CustomThreadCount = """custom_threads\{poolName="all",\} (\d+\.\d+)""".r

  /**
   * A thread source returning each of `samples` in turn, then an empty list of threads.
   */
  private def scriptedThreadNames(
    samples: IO[List[MeterThreads.ThreadName]]*,
  ): IO[IO[List[MeterThreads.ThreadName]]] = {
    Ref.of[IO, List[IO[List[MeterThreads.ThreadName]]]](samples.toList).map { scriptRef =>
      scriptRef
        .modify {
          case next :: rest => (rest, next)
          case Nil => (List.empty, IO.pure(List.empty))
        }
        .flatten
    }
  }

  /**
   * Waits for a sample to be gauged, bounded by the suite timeout.
   */
  private def gaugedThreadCount(prometheus: Prometheus[IO]): IO[Double] = {
    val sampled = prometheus.write004.map { exported =>
      CustomThreadCount.findFirstMatchIn(exported).map { matched => matched.group(1).toDouble }
    }
    (IO.sleep(10.millis) *> sampled).untilDefinedM
  }

  private def probeThread(name: String): Resource[IO, Unit] = {
    val start = IO.blocking {
      val started = new CountDownLatch(1)
      val stopped = new CountDownLatch(1)
      val runnable: Runnable = { () =>
        started.countDown()
        stopped.await()
      }
      val thread = new Thread(runnable, name)
      thread.setDaemon(true)
      thread.start()
      started.await()
      stopped
    }
    Resource.make(start) { stopped => IO.blocking { stopped.countDown() } }.void
  }

  private class RecordingMetrics(observationsRef: Ref[IO, List[(String, Int)]]) extends MeterThreads.Metrics[IO] {

    def threads(poolName: String, threads: Int): IO[Unit] = {
      observationsRef.update { observations => observations :+ (poolName -> threads) }
    }

    def observations: IO[List[(String, Int)]] = observationsRef.get
  }

  private object RecordingMetrics {

    def of: IO[RecordingMetrics] = {
      Ref.of[IO, List[(String, Int)]](List.empty).map { observationsRef => new RecordingMetrics(observationsRef) }
    }
  }
}
