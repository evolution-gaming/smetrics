package com.evolution.smetrics.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.UnsynchronizedAppenderBase
import io.prometheus.metrics.core.metrics.{Counter => PrometheusCounter}

class InstrumentedAppender extends UnsynchronizedAppenderBase[ILoggingEvent] {

  override protected def append(eventObject: ILoggingEvent): Unit =
    eventObject.getLevel() match {
      case Level.TRACE => InstrumentedAppender.TraceCounter.inc()
      case Level.DEBUG => InstrumentedAppender.DebugCounter.inc()
      case Level.INFO  => InstrumentedAppender.InfoCounter.inc()
      case Level.WARN  => InstrumentedAppender.WarnCounter.inc()
      case Level.ERROR => InstrumentedAppender.ErrorCounter.inc()
      case _           => ()
    }

}

object InstrumentedAppender {
  val Counter =
    PrometheusCounter
      .builder()
      .name("logback_appender_total")
      .help("Logback log statements at various log levels")
      .labelNames("level")
      .register()
  val TraceCounter = Counter.labelValues("trace")
  val DebugCounter = Counter.labelValues("debug")
  val InfoCounter  = Counter.labelValues("info")
  val WarnCounter  = Counter.labelValues("warn")
  val ErrorCounter = Counter.labelValues("error")
}
