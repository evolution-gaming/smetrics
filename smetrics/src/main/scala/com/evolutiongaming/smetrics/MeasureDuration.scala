package com.evolutiongaming.smetrics

import java.util.concurrent.TimeUnit

import cats.effect.Clock
import cats.implicits._
import cats.{Applicative, FlatMap, ~>}

import scala.concurrent.duration._

@deprecated("Use com.evolutiongaming.catshelper.MeasureDuration from cats-helper 3.5.0 and up", "1.2.0")
trait MeasureDuration[F[_]] {

  def start: F[F[FiniteDuration]]
}

object MeasureDuration {

  def const[F[_]](value: F[F[FiniteDuration]]): MeasureDuration[F] = new MeasureDuration[F] {
    def start = value
  }


  def empty[F[_]: Applicative]: MeasureDuration[F] = const(0.seconds.pure[F].pure[F])


  def apply[F[_]](implicit F: MeasureDuration[F]): MeasureDuration[F] = F


  def fromClock[F[_]: FlatMap](clock: Clock[F]): MeasureDuration[F] = {
    fromClock1(clock, FlatMap[F])
  }

  implicit def fromClock1[F[_]: Clock: FlatMap]: MeasureDuration[F] = {
    val timeUnit = TimeUnit.NANOSECONDS
    val duration = for {
      duration <- Clock[F].monotonic(timeUnit)
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


  implicit class MeasureDurationOps[F[_]](val self: MeasureDuration[F]) extends AnyVal {

    def mapK[G[_]](f: F ~> G)(implicit F: FlatMap[F]): MeasureDuration[G] = new MeasureDuration[G] {

      val start = f(self.start.map(f.apply))
    }
  }

}