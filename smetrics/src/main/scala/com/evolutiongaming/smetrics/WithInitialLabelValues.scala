package com.evolutiongaming.smetrics

import cats.data.{NonEmptyList => Nel}
import com.evolutiongaming.smetrics.CollectionHelper._

trait WithInitialLabelValues[A[_]] {

  def apply[B](f: List[List[String]] => B): A[B]
}

object WithInitialLabelValues {

  def apply[F[_]](implicit F: WithInitialLabelValues[F]): WithInitialLabelValues[F] = F


  implicit val withInitialLabelValues0: WithInitialLabelValues[InitialLabelValues.`0`] = new WithInitialLabelValues[InitialLabelValues.`0`] {

    def apply[B](f: List[List[String]] => B): B = f(List.empty)
  }

  implicit val withInitialLabelValues1: WithInitialLabelValues[InitialLabelValues.`1`] = new WithInitialLabelValues[InitialLabelValues.`1`] {

    def apply[B](f: List[List[String]] => B) = new InitialLabelValues.`1`[B] {

      def labelValues(values: Nel[String]): B =
        f(List(values.toList).generateCombinations)
    }
  }


  implicit val withInitialLabelValues2: WithInitialLabelValues[InitialLabelValues.`2`] = new WithInitialLabelValues[InitialLabelValues.`2`] {

    def apply[B](f: List[List[String]] => B) = new InitialLabelValues.`2`[B] {

      def labelValues(values1: Nel[String], values2: Nel[String]) =
        f(List(values1.toList, values2.toList).generateCombinations)
    }
  }


  implicit val withInitialLabelValues3: WithInitialLabelValues[InitialLabelValues.`3`] = new WithInitialLabelValues[InitialLabelValues.`3`] {

    def apply[B](f: List[List[String]] => B) = new InitialLabelValues.`3`[B] {

      def labelValues(values1: Nel[String], values2: Nel[String], values3: Nel[String]) = {
        f(List(values1.toList, values2.toList, values3.toList).generateCombinations)
      }
    }
  }


  implicit val withInitialLabelValues4: WithInitialLabelValues[InitialLabelValues.`4`] = new WithInitialLabelValues[InitialLabelValues.`4`] {

    def apply[B](f: List[List[String]] => B) = new InitialLabelValues.`4`[B] {

      def labelValues(values1: Nel[String], values2: Nel[String], values3: Nel[String], values4: Nel[String]) = {
        f(List(values1.toList, values2.toList, values3.toList, values4.toList).generateCombinations)
      }
    }
  }


  implicit val withInitialLabelValues5: WithInitialLabelValues[InitialLabelValues.`5`] = new WithInitialLabelValues[InitialLabelValues.`5`] {

    def apply[B](f: List[List[String]] => B) = new InitialLabelValues.`5`[B] {

      def labelValues(values1: Nel[String], values2: Nel[String], values3: Nel[String], values4: Nel[String], values5: Nel[String]) = {
        f(List(values1.toList, values2.toList, values3.toList, values4.toList, values5.toList).generateCombinations)
      }
    }
  }


  implicit val withInitialLabelValues6: WithInitialLabelValues[InitialLabelValues.`6`] = new WithInitialLabelValues[InitialLabelValues.`6`] {

    def apply[B](f: List[List[String]] => B) = new InitialLabelValues.`6`[B] {

      def labelValues(values1: Nel[String], values2: Nel[String], values3: Nel[String], values4: Nel[String], values5: Nel[String], values6: Nel[String]) = {
        f(List(values1.toList, values2.toList, values3.toList, values4.toList, values5.toList, values6.toList).generateCombinations)
      }
    }
  }


  implicit val withInitialLabelValues7: WithInitialLabelValues[InitialLabelValues.`7`] = new WithInitialLabelValues[InitialLabelValues.`7`] {

    def apply[B](f: List[List[String]] => B) = new InitialLabelValues.`7`[B] {

      def labelValues(values1: Nel[String], values2: Nel[String], values3: Nel[String], values4: Nel[String], values5: Nel[String], values6: Nel[String], values7: Nel[String]) = {
        f(List(values1.toList, values2.toList, values3.toList, values4.toList, values5.toList, values6.toList, values7.toList).generateCombinations)
      }
    }
  }
}