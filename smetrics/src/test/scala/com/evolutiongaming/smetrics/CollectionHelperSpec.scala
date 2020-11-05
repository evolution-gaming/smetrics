package com.evolutiongaming.smetrics

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import com.evolutiongaming.smetrics.CollectionHelper._

class CollectionHelperSpec extends AnyFunSuite with Matchers {

  test("generate combinations for empty list") {
    List.empty[List[Int]].combinations() shouldEqual List.empty[List[Int]]
    List(List.empty[Int], List.empty[Int]).combinations() shouldEqual List.empty[List[Int]]
    List(List.empty[Int], List.empty[Int], List.empty[Int]).combinations() shouldEqual List.empty[List[Int]]
  }

  test("generate combinations for singleton list") {
    List(List(1, 2, 3)).combinations() shouldEqual List(List(1), List(2), List(3))
  }

  test("generate combinations for list") {
    List(List(1, 2), List(3, 4)).combinations() shouldEqual List(List(1, 3), List(1, 4), List(2, 3), List(2, 4))

    List(List(1, 2), List(3, 4), List(5, 6)).combinations() shouldEqual
      List(List(1, 3, 5), List(1, 3, 6), List(1, 4, 5), List(1, 4, 6), List(2, 3, 5), List(2, 3, 6), List(2, 4, 5), List(2, 4, 6))

    List(List(1, 2, 3), List(4, 5, 6)).combinations() shouldEqual
      List(List(1, 4), List(1, 5), List(1, 6), List(2, 4), List(2, 5), List(2, 6), List(3, 4), List(3, 5), List(3, 6))
  }
}