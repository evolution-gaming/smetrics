package com.evolutiongaming.smetrics

import com.evolutiongaming.smetrics.MetricsHelper._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MetricsHelperSpec extends AnyFunSuite with Matchers {

  test("nanosToSeconds") {
    1000000000.nanosToSeconds shouldEqual 1
  }

  test("nanosToMillis") {
    1000000.nanosToMillis shouldEqual 1
  }

  test("millisToSeconds") {
    1000.millisToSeconds shouldEqual 1
  }
}
