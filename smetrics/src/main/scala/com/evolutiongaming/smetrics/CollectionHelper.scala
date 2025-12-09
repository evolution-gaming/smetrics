package com.evolutiongaming.smetrics

import cats.implicits.*

object CollectionHelper {

  implicit class ListOpsCollectionHelper[A](val values: List[List[A]]) extends AnyVal {

    /*
      Generates all possible combinations for transmitted lists.

      Examples:
        List.empty -> List.empty
        List(1,2,3)) -> List(List(1), List(2), List(3))
        List(1,2), List(3,4)) -> List(List(1,3), List(1,4), List(2,3), List(2,4))
        List(List(1, 2), List(3,4), List(5,6)) -> List(List(1, 3, 5), List(1, 3, 6), List(1, 4, 5), List(1, 4, 6), List(2, 3, 5), List(2, 3, 6), List(2, 4, 5), List(2, 4, 6))
     */
    def combine: List[List[A]] = {
      values match {
        case Nil => List.empty
        case values :: Nil => values.map(List(_))
        case values1 :: values2 :: tail =>
          tail.foldLeft(
            values1.map2(values2)(List(_, _)),
          )((accumulator, values) =>
            for {
              value1 <- accumulator
              value2 <- values
            } yield value1 :+ value2,
          )
      }
    }
  }
}
