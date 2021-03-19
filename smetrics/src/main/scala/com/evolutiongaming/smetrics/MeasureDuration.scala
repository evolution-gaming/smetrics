package com.evolutiongaming.smetrics

import java.util.concurrent.TimeUnit

import cats.effect.Clock
import cats.implicits._
import cats.{Applicative, FlatMap, Monad, MonadError, ~>}

import scala.concurrent.duration._

trait MeasureDuration[F[_]] {

  def start: F[F[FiniteDuration]]
}

object MeasureDuration {

  def const[F[_]](value: F[F[FiniteDuration]]): MeasureDuration[F] = new MeasureDuration[F] {
    def start = value
  }


  def empty[F[_] : Applicative]: MeasureDuration[F] = const(0.seconds.pure[F].pure[F])


  def apply[F[_]](implicit F: MeasureDuration[F]): MeasureDuration[F] = F


  implicit def fromClock[F[_] : FlatMap](implicit clock: Clock[F]): MeasureDuration[F] = {
    val timeUnit = TimeUnit.NANOSECONDS
    val duration = for {
      duration <- clock.monotonic(timeUnit)
    } yield {
      FiniteDuration(duration, timeUnit)
    }
    fromDuration(duration)
  }


  def fromDuration[F[_] : FlatMap](time: F[FiniteDuration]): MeasureDuration[F] = {
    new MeasureDuration[F] {
      val start = {
        for {
          start <- time
        } yield for {
          end <- time
        } yield {
          end - start
        }
      }
    }
  }


  implicit class MeasureDurationOps[F[_]](val self: MeasureDuration[F]) extends AnyVal {

    def mapK[G[_]](f: F ~> G)(implicit F: FlatMap[F]): MeasureDuration[G] = new MeasureDuration[G] {

      val start = f(self.start.map(f.apply))
    }
  }

  object implicits {
    implicit class OpsMeasuredDuration[F[_], A](val fa: F[A]) extends AnyVal {
      def measured(
        handleF: FiniteDuration => F[Unit]
      )(implicit F: Monad[F], measureDuration: MeasureDuration[F]): F[A] =
        for {
          measure <- measureDuration.start
          result <- fa
          duration <- measure
          _ <- handleF(duration)
        } yield result
      def measuredCase[E](
        successF: FiniteDuration => F[Unit],
        failureF: FiniteDuration => F[Unit]
      )(implicit F: MonadError[F, E], measureDuration: MeasureDuration[F]): F[A] =
        for {
          measure <- measureDuration.start
          result <- fa.attempt
          duration <- measure
          _ <- result match {
            case Right(_) => successF(duration)
            case Left(_)  => failureF(duration)
          }
          result <- result.liftTo[F]
        } yield result
    }
  }

}