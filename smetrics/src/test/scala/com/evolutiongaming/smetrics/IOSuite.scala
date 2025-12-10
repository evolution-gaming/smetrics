package com.evolutiongaming.smetrics

import cats.effect.unsafe.implicits.global
import cats.effect.{Clock, IO}
import com.evolutiongaming.catshelper.{FromFuture, MeasureDuration}
import org.scalatest.Succeeded

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object IOSuite {
  val Timeout: FiniteDuration = 10.seconds

  implicit val executor: ExecutionContextExecutor = ExecutionContext.global

  implicit val measureDuration: MeasureDuration[IO] = MeasureDuration.fromClock(Clock[IO])
  implicit val fromFutureIO: FromFuture[IO] = FromFuture.lift[IO]

  def runIO[A](io: IO[A], timeout: FiniteDuration = Timeout): Future[Succeeded.type] = {
    io.timeout(timeout).as(Succeeded).unsafeToFuture()
  }

  implicit class IOOps[A](val self: IO[A]) extends AnyVal {
    def run(timeout: FiniteDuration = Timeout): Future[Succeeded.type] = runIO(self, timeout)
  }
}
