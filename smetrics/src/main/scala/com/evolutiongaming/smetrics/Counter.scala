package com.evolutiongaming.smetrics

import cats.Applicative
import cats.implicits._

trait Counter[F[_]] {

  def inc(value: Double = 1.0): F[Unit]
}

object Counter {

  def empty[F[_] : Applicative]: Counter[F] = const(().pure[F])

  def const[F[_]](unit: F[Unit]): Counter[F] = new Counter[F] {

    def inc(value: Double) = unit
  }
}