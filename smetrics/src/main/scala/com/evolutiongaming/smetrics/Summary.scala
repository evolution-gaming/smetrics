package com.evolutiongaming.smetrics

import cats.implicits._
import cats.{Applicative, ~>}

trait Summary[F[_]] {

  def observe(value: Double): F[Unit]
}

object Summary {

  def empty[F[_] : Applicative]: Summary[F] = const(().pure[F])

  def const[F[_]](unit: F[Unit]): Summary[F] = new Summary[F] {

    def observe(value: Double) = unit
  }


  implicit class SummaryOps[F[_]](val self: Summary[F]) extends AnyVal {

    def mapK[G[_]](f: F ~> G): Summary[G] = new Summary[G] {

      def observe(value: Double) = f(self.observe(value))
    }
  }
}
