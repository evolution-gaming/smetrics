package com.evolutiongaming.smetrics

import cats.implicits._
import cats.{Applicative, ~>}

trait Counter[F[_]] {

  def inc(value: Double = 1.0): F[Unit]
}

object Counter {

  def empty[F[_]: Applicative]: Counter[F] = const(().pure[F])

  def const[F[_]](unit: F[Unit]): Counter[F] = (_: Double) => unit

  implicit class CounterOps[F[_]](val self: Counter[F]) extends AnyVal {

    def mapK[G[_]](f: F ~> G): Counter[G] = (value: Double) => f(self.inc(value))
  }
}
