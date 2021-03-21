package com.evolutiongaming.smetrics

import java.util.concurrent.TimeUnit

import cats.Id
import cats.effect.{Clock, IO, Timer}
import cats.syntax.functor._
import cats.syntax.applicativeError._
import com.evolutiongaming.smetrics.syntax.measureDuration._

import scala.concurrent.duration._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import IOSuite._
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar}
import org.mockito.scalatest.MockitoSugar

class MeasureDurationSpec extends AnyFunSuite with Matchers with MockitoSugar with ArgumentMatchersSugar {

  import MeasureDurationSpec._

  test("measure duration") {
    val measureDuration = MeasureDuration[StateT]
    val stateT = for {
      duration <- measureDuration.start
      duration <- duration
    } yield duration

    val (state, duration) = stateT.run(State(List(1, 3)))
    duration shouldEqual 2.nanos
    state shouldEqual State.Empty
  }

  test("MeasureDurationOps.measured") {
    val handler: FiniteDuration => StateT[Unit] =
      duration => StateT { state =>
        val timestamp = duration.toNanos
        timestamp shouldEqual 5
        (state, timestamp)
      }.void
    val (state, _) = Timer[StateT]
      .sleep(5.nanos)
      .measured(handler)
      .run(State.Empty)
    state shouldEqual State.Empty
  }

  test("MeasureDurationOps.measuredCase success") {
    val successHandler = mock[FiniteDuration => IO[Unit]]
    val failureHandler = mock[FiniteDuration => IO[Unit]]
    val argument: ArgumentCaptor[FiniteDuration] = ArgumentCaptor.forClass(classOf[FiniteDuration])
    doReturn(IO.unit).when(successHandler).apply(argument.capture())
    Timer[IO]
      .sleep(3.second)
      .measuredCase(successHandler, failureHandler)
      .unsafeRunSync()
    assertResult(3)(argument.getValue.toSeconds)
    verifyNoMoreInteractions(successHandler, failureHandler)
  }
  
  test("MeasureDurationOps.measuredCase failure") {
    val successHandler = mock[FiniteDuration => IO[Unit]]
    val failureHandler = mock[FiniteDuration => IO[Unit]]
    val argument: ArgumentCaptor[FiniteDuration] = ArgumentCaptor.forClass(classOf[FiniteDuration])
    doReturn(IO.unit).when(failureHandler).apply(argument.capture())
    assertThrows[RuntimeException] {
      (Timer[IO].sleep(3.second) *> new RuntimeException().raiseError[IO, Unit])
        .measuredCase(successHandler, failureHandler)
        .unsafeRunSync()
    }
    assertResult(3)(argument.getValue.toSeconds)
    verifyNoMoreInteractions(successHandler, failureHandler)
  }

}

object MeasureDurationSpec {

  final case class State(timestamps: List[Long]) {

    def timestamp: (State, Long) = {
      timestamps match {
        case a :: timestamps => (copy(timestamps = timestamps), a)
        case Nil             => (this, 0)
      }
    }
  }

  object State {
    val Empty: State = State(List.empty)
  }


  type StateT[A] = cats.data.StateT[Id, State, A]

  object StateT {

    def apply[A](f: State => (State, A)): StateT[A] = cats.data.StateT[Id, State, A](f)
  }


  implicit val timerStateT: Timer[StateT] = new Timer[StateT] {

    def clock: Clock[StateT] = new Clock[StateT] {
      def realTime(unit: TimeUnit) =
        StateT { state =>
          val (state1, timestamp) = state.timestamp
          val timestamp1 = unit.convert(timestamp, TimeUnit.NANOSECONDS)
          (state1, timestamp1)
        }

      def monotonic(unit: TimeUnit) =
        StateT { state =>
          val (state1, timestamp) = state.timestamp
          val timestamp1 = unit.convert(timestamp, TimeUnit.NANOSECONDS)
          (state1, timestamp1)
        }
    }

    def sleep(duration: FiniteDuration) =
      StateT { state =>
        val (state1, timestamp) = state.timestamp
        val timestamp1 = timestamp + duration.toNanos
        val newState = state1.copy(timestamps = state1.timestamps :+ timestamp1)
        (newState, ())
      }
  }
}