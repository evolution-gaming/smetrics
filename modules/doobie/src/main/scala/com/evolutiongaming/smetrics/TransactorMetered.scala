package com.evolutiongaming.smetrics

import java.sql.Connection
import cats.data.Kleisli
import cats.effect.BracketThrow
import cats.implicits._
import doobie.free.connection.ConnectionOp
import doobie.util.transactor.{Interpreter, Transactor}

import scala.annotation.nowarn

object TransactorMetered {

  @nowarn("msg=deprecated")
  def apply[F[_]: BracketThrow: MeasureDuration](
    transactor: Transactor[F],
    metrics: DoobieMetrics[F]
  ): Transactor[F] =
    transactor.copy(interpret0 = new Interpreter[F] {
      override def apply[A](fa: ConnectionOp[A]): Kleisli[F, Connection, A] =
        transactor.interpret(fa).mapF { query =>
          for {
            start    <- MeasureDuration[F].start
            result   <- query.attempt
            duration <- start
            _        <- metrics.query(duration, result.isRight)
            result   <- result.liftTo[F]
          } yield result
        }
    })
}
