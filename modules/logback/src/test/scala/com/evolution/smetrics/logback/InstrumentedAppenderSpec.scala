package com.evolution.smetrics.logback

import cats.effect.IO
import cats.syntax.all.*
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.{Level, LoggerContext}
import ch.qos.logback.core.status.Status
import com.evolution.smetrics.logback.InstrumentedAppender.{LevelCounters, registerCounters}
import io.prometheus.metrics.model.registry.{Collector, PrometheusRegistry}
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import weaver.SimpleIOSuite

import scala.jdk.CollectionConverters.*

object InstrumentedAppenderSpec extends SimpleIOSuite {

  test("count log events per level") {
    val levels = List(Level.TRACE, Level.DEBUG, Level.INFO, Level.INFO, Level.WARN, Level.ERROR)
    for {
      registry <- IO(new PrometheusRegistry())
      appender <- IO(appenderWith(registerCounters(registry)))
      _ <- IO(appender.start())
      _ <- levels.traverse_ { level => IO(appender.doAppend(event(level))) }
      counts <- IO(levelCounts(registry))
    } yield expect(
      clue(counts) == Map(
        "trace" -> 1d,
        "debug" -> 1d,
        "info" -> 2d,
        "warn" -> 1d,
        "error" -> 1d,
      ),
    )
  }

  test("ignore events with unmapped levels") {
    for {
      registry <- IO(new PrometheusRegistry())
      appender <- IO(appenderWith(registerCounters(registry)))
      _ <- IO(appender.start())
      _ <- IO(appender.doAppend(event(Level.OFF)))
      counts <- IO(levelCounts(registry))
    } yield expect(clue(counts).values.sum == 0d)
  }

  test("share the counters between appender instances, as logback re-creates appenders on config reload") {
    for {
      registry <- IO(new PrometheusRegistry())
      counters <- IO(registerCounters(registry))
      firstAppender <- IO(appenderWith(counters))
      secondAppender <- IO(appenderWith(counters))
      _ <- IO(firstAppender.start())
      _ <- IO(secondAppender.start())
      _ <- IO(firstAppender.doAppend(event(Level.INFO)))
      _ <- IO(secondAppender.doAppend(event(Level.INFO)))
      counts <- IO(levelCounts(registry))
    } yield expect(clue(counts).get("info").contains(2d))
  }

  test("return the failure instead of throwing when the counter name is already registered") {
    for {
      registry <- IO(new PrometheusRegistry())
      firstAttempt <- IO(registerCounters(registry))
      secondAttempt <- IO(registerCounters(registry))
    } yield expect.all(
      clue(firstAttempt).isRight,
      clue(secondAttempt).left.exists {
        case _: IllegalArgumentException => true
        case _ => false
      },
    )
  }

  test("doAppend does not throw when the counter registration failed") {
    val levels = List(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR)
    for {
      appender <- IO(appenderWith(failedRegistration()))
      _ <- IO(appender.setContext(new LoggerContext()))
      _ <- IO(appender.start())
      _ <- levels.traverse_ { level => IO(appender.doAppend(event(level))) }
    } yield expect(clue(appender.isStarted))
  }

  test("report the registration failure to the logback status system on start") {
    for {
      appender <- IO(appenderWith(failedRegistration()))
      loggerContext <- IO(new LoggerContext())
      _ <- IO(appender.setContext(loggerContext))
      _ <- IO(appender.start())
      statuses <- IO(loggerContext.getStatusManager.getCopyOfStatusList.asScala.toList)
    } yield expect(
      clue(statuses.map { status => (status.getLevel, status.getMessage) }).exists { case (level, message) =>
        level == Status.ERROR && message.contains("logback_appender_total")
      },
    )
  }

  pureTest("registerCounters rethrows JVM-fatal errors") {
    val registry: PrometheusRegistry = new PrometheusRegistry() {
      override def register(collector: Collector): Unit = throw new OutOfMemoryError("test")
    }
    // Either.catchOnly matches on the class and thus, unlike util.Try, sees fatal errors
    val outcome = Either.catchOnly[OutOfMemoryError](registerCounters(registry))
    expect(clue(outcome).isLeft)
  }

  test("share one JVM-wide registration result between appenders by default") {
    for {
      firstAppender <- IO(new InstrumentedAppender())
      secondAppender <- IO(new InstrumentedAppender())
    } yield expect(firstAppender.counters eq secondAppender.counters)
  }

  private def appenderWith(resolvedCounters: Either[Throwable, LevelCounters]): InstrumentedAppender =
    new InstrumentedAppender {
      override private[logback] def counters: Either[Throwable, LevelCounters] = resolvedCounters
    }

  private def failedRegistration(): Either[Throwable, LevelCounters] = {
    val registry = new PrometheusRegistry()
    val _ = registerCounters(registry)
    registerCounters(registry)
  }

  private def event(level: Level): LoggingEvent = {
    val loggingEvent = new LoggingEvent()
    loggingEvent.setLevel(level)
    loggingEvent
  }

  private def levelCounts(registry: PrometheusRegistry): Map[String, Double] =
    registry
      .scrape()
      .asScala
      .collect { case counterSnapshot: CounterSnapshot => counterSnapshot }
      .flatMap { counterSnapshot => counterSnapshot.getDataPoints.asScala }
      .map { dataPoint => dataPoint.getLabels.get("level") -> dataPoint.getValue }
      .toMap

}
