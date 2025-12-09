package com.evolutiongaming.smetrics

import cats.data.{NonEmptyList => Nel}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BucketsSpec extends AnyFunSuite with Matchers {

  for {
    (start, width, count, expected) <- List(
      (1.0, 1.0, 0, Buckets(Nel.of(1.0))),
      (1.0, 1.0, 3, Buckets(Nel.of(1.0, 2.0, 3.0))),
      (1.0, 0.0, 2, Buckets(Nel.of(1.0, 1.0))),
    )
  } {
    test(s"linear start: $start, width: $width, count: $count, expected: $expected") {
      Buckets.linear(start = start, width = width, count = count) shouldEqual expected
    }
  }

  for {
    (start, factor, count, expected) <- List(
      (1.0, 2.0, 0, Buckets(Nel.of(1.0))),
      (1.0, 2.0, 3, Buckets(Nel.of(1.0, 2.0, 4.0))),
      (1.0, 1.0, 2, Buckets(Nel.of(1.0, 1.0))),
    )
  } {
    test(s"exponential start: $start, factor: $factor, count: $count, expected: $expected") {
      Buckets.exponential(start = start, factor = factor, count = count) shouldEqual expected
    }
  }
}
