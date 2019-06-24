package com.evolutiongaming.smetrics

import cats.Applicative
import cats.implicits._

trait Summary[F[_]] {

  def observe(value: Double): F[Unit]
}

object Summary {

  def empty[F[_] : Applicative]: Summary[F] = const(().pure[F])

  def const[F[_]](unit: F[Unit]): Summary[F] = new Summary[F] {

    def observe(value: Double) = unit
  }
}
