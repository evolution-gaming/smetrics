package com.evolutiongaming.smetrics

import cats.syntax.all._
import cats.{Applicative, ~>}

trait Gauge[F[_]] {

  def inc(value: Double = 1.0): F[Unit]

  def dec(value: Double = 1.0): F[Unit]

  def set(value: Double): F[Unit]
}

object Gauge {

  def empty[F[_] : Applicative]: Gauge[F] = const(().pure[F])

  def const[F[_]](unit: F[Unit]): Gauge[F] = new Gauge[F] {

    def inc(value: Double) = unit

    def dec(value: Double) = unit

    def set(value: Double) = unit
  }


  implicit class GaugeOps[F[_]](val self: Gauge[F]) extends AnyVal {

    def mapK[G[_]](f: F ~> G): Gauge[G] = new Gauge[G] {

      def inc(value: Double) = f(self.inc(value))

      def dec(value: Double) = f(self.dec(value))

      def set(value: Double) = f(self.set(value))
    }
  }
}