package com.evolutiongaming.smetrics

import cats.syntax.all.*
import cats.{Applicative, ~>}

trait Summary[F[_]] {

  def observe(value: Double): F[Unit]
}

object Summary {

  def empty[F[_]: Applicative]: Summary[F] = const(().pure[F])

  def const[F[_]](unit: F[Unit]): Summary[F] = (_: Double) => unit

  implicit class SummaryOps[F[_]](val self: Summary[F]) extends AnyVal {

    def mapK[G[_]](f: F ~> G): Summary[G] = (value: Double) => f(self.observe(value))
  }
}
