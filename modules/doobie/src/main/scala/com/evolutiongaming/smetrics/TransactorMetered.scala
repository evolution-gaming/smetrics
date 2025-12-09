package com.evolutiongaming.smetrics

import cats.data.Kleisli
import cats.effect.MonadCancelThrow
import cats.implicits._
import com.evolutiongaming.catshelper.MeasureDuration
import doobie.free.connection.ConnectionOp
import doobie.util.transactor.{Interpreter, Transactor}

import java.sql.Connection

object TransactorMetered {

  def apply[F[_]: MonadCancelThrow: MeasureDuration](
    transactor: Transactor[F],
    metrics: DoobieMetrics[F],
  ): Transactor[F] =
    transactor.copy(interpret0 = new Interpreter[F] {
      override def apply[A](fa: ConnectionOp[A]): Kleisli[F, Connection, A] =
        transactor.interpret(fa).mapF { query =>
          for {
            start <- MeasureDuration[F].start
            result <- query.attempt
            duration <- start
            _ <- metrics.query(duration, result.isRight)
            result <- result.liftTo[F]
          } yield result
        }
    })
}
