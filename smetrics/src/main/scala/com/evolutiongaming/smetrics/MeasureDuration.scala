package com.evolutiongaming.smetrics

import java.util.concurrent.TimeUnit

import cats.FlatMap
import cats.effect.Clock
import cats.implicits._

import scala.concurrent.duration.FiniteDuration

trait MeasureDuration[F[_]] {

  def start: F[F[FiniteDuration]]
}

object MeasureDuration {

  def apply[F[_]](implicit F: MeasureDuration[F]): MeasureDuration[F] = F


  def fromClock[F[_] : FlatMap](clock: Clock[F]): MeasureDuration[F] = {
    val timeUnit = TimeUnit.NANOSECONDS
    val duration = for {
      duration <- clock.monotonic(timeUnit)
    } yield {
      FiniteDuration(duration, timeUnit)
    }
    fromDuration(duration)
  }


  def fromDuration[F[_] : FlatMap](time: F[FiniteDuration]): MeasureDuration[F] = {
    new MeasureDuration[F] {
      val start = {
        for {
          start <- time
        } yield for {
          end <- time
        } yield {
          end - start
        }
      }
    }
  }
}