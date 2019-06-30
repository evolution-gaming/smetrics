package com.evolutiongaming.smetrics

import com.evolutiongaming.smetrics.MetricsHelper._
import org.scalatest.{FunSuite, Matchers}

class MetricsHelperSpec extends FunSuite with Matchers {

  test("nanosToSeconds") {
    1000000000.nanosToSeconds shouldEqual 1
  }

  test("millisToSeconds") {
    1000.millisToSeconds shouldEqual 1
  }
}
