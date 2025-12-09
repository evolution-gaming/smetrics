package com.evolutiongaming.smetrics

import cats.implicits.*
import cats.{Applicative, ~>}

trait Info[F[_]] {
  def set(): F[Unit]
}

object Info {

  def empty[F[_]: Applicative]: Info[F] = const(().pure[F])

  def const[F[_]](unit: F[Unit]): Info[F] = new Info[F] {
    def set(): F[Unit] = unit
  }

  implicit class InfoOps[F[_]](val self: Info[F]) extends AnyVal {

    def mapK[G[_]](f: F ~> G): Info[G] = new Info[G] {
      def set(): G[Unit] = f(self.set())
    }
  }
}
