package com.evolution.smetrics.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import io.prometheus.metrics.core.metrics.Counter as PrometheusCounter
import io.prometheus.metrics.model.registry.PrometheusRegistry

/**
 * Counts log statements per log level in the `logback_appender_total` Prometheus counter.
 *
 * The counter is registered once per JVM: logback re-creates appender instances on every
 * configuration reload (`scan="true"`), while the Prometheus registry rejects duplicate
 * registrations.
 *
 * A failed registration must not break logging: an `Error` escaping [[append]] would propagate
 * through logback into the application code calling the logger, because logback catches only
 * `Exception`. Hence the registration failure is kept as a value, reported to the logback status
 * system on [[start]], and [[append]] becomes a no-op.
 */
class InstrumentedAppender extends UnsynchronizedAppenderBase[ILoggingEvent] {

  private[logback] def counters: Either[Throwable, InstrumentedAppender.LevelCounters] =
    InstrumentedAppender.defaultCounters

  override def start(): Unit = {
    counters.left.foreach { cause =>
      addError(
        "failed to register the logback_appender_total Prometheus counter, " +
          "log level metrics will not be reported",
        cause,
      )
    }
    super.start()
  }

  override protected def append(eventObject: ILoggingEvent): Unit =
    counters match {
      case Right(levelCounters) => levelCounters.inc(eventObject.getLevel)
      case Left(_) => ()
    }

}

private[logback] object InstrumentedAppender {

  private[logback] final class LevelCounters(counter: PrometheusCounter) {
    private val traceCounter = counter.labelValues("trace")
    private val debugCounter = counter.labelValues("debug")
    private val infoCounter = counter.labelValues("info")
    private val warnCounter = counter.labelValues("warn")
    private val errorCounter = counter.labelValues("error")

    def inc(level: Level): Unit =
      level match {
        case Level.TRACE => traceCounter.inc()
        case Level.DEBUG => debugCounter.inc()
        case Level.INFO => infoCounter.inc()
        case Level.WARN => warnCounter.inc()
        case Level.ERROR => errorCounter.inc()
        case _ => ()
      }
  }

  private[logback] lazy val defaultCounters: Either[Throwable, LevelCounters] =
    registerCounters(PrometheusRegistry.defaultRegistry)

  private[logback] def registerCounters(registry: PrometheusRegistry): Either[Throwable, LevelCounters] =
    try {
      val counter = PrometheusCounter
        .builder()
        .name("logback_appender_total")
        .help("Logback log statements at various log levels")
        .labelNames("level")
        .register(registry)
      Right(new LevelCounters(counter))
    } catch {
      // never swallow JVM-fatal errors; anything else (incl. LinkageError) must not break logging
      case cause: VirtualMachineError => throw cause
      case cause: InterruptedException =>
        Thread.currentThread().interrupt()
        Left(cause)
      case cause: Throwable => Left(cause)
    }

}
