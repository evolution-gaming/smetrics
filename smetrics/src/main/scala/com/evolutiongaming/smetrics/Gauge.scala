package com.evolutiongaming.smetrics

import cats.Applicative
import cats.implicits._

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
}