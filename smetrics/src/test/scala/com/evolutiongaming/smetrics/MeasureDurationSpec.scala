package com.evolutiongaming.smetrics

import java.util.concurrent.TimeUnit

import cats.Id
import cats.effect.Clock

import scala.concurrent.duration._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MeasureDurationSpec extends AnyFunSuite with Matchers {

  import MeasureDurationSpec._

  test("measure duration") {
    val measureDuration = MeasureDuration.fromClock[StateT](Clock[StateT])
    val stateT = for {
      duration <- measureDuration.start
      duration <- duration
    } yield duration

    val (state, duration) = stateT.run(State(List(1, 3)))
    duration shouldEqual 2.nanos
    state shouldEqual State.Empty
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


  implicit val clockStateT: Clock[StateT] = new Clock[StateT] {

    def realTime(unit: TimeUnit) = {
      StateT { state =>
        val (state1, timestamp) = state.timestamp
        val timestamp1 = unit.convert(timestamp, TimeUnit.NANOSECONDS)
        (state1, timestamp1)
      }
    }

    def monotonic(unit: TimeUnit) = {
      StateT { state =>
        val (state1, timestamp) = state.timestamp
        val timestamp1 = unit.convert(timestamp, TimeUnit.NANOSECONDS)
        (state1, timestamp1)
      }
    }
  }
}
