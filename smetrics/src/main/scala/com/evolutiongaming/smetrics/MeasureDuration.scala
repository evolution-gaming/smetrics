package com.evolutiongaming.smetrics

import cats.FlatMap
import cats.effect.Clock
import cats.implicits._
import com.evolutiongaming.catshelper.ClockHelper._

object MeasureDuration {

  def apply[F[_] : FlatMap : Clock]: F[F[Long]] = apply(Clock[F].nanos)

  def apply[F[_] : FlatMap](time: F[Long]): F[F[Long]] = {
    for {
      start <- time
    } yield for {
      end   <- time
    } yield {
      end - start
    }
  }
}