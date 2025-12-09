package com.evolutiongaming.smetrics

import cats.syntax.all.*
import cats.{Applicative, ~>}

trait Histogram[F[_]] {

  def observe(value: Double): F[Unit]
}

object Histogram {

  def empty[F[_]: Applicative]: Histogram[F] = const(().pure[F])

  def const[F[_]](unit: F[Unit]): Histogram[F] = (_: Double) => unit

  implicit class HistogramOps[F[_]](val self: Histogram[F]) extends AnyVal {

    def mapK[G[_]](f: F ~> G): Histogram[G] = (value: Double) => f(self.observe(value))
  }
}
