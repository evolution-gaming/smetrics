package com.evolutiongaming.smetrics

trait WithLabelValues[A[_]] {

  def apply[B](f: List[String] => B): A[B]
}

object WithLabelValues {

  def apply[F[_]](implicit F: WithLabelValues[F]): WithLabelValues[F] = F

  implicit val withLabelValues0: WithLabelValues[LabelValues.`0`] = new WithLabelValues[LabelValues.`0`] {

    def apply[B](f: List[String] => B) = f(List.empty)
  }

  implicit val withLabelValues1: WithLabelValues[LabelValues.`1`] = new WithLabelValues[LabelValues.`1`] {

    def apply[B](f: List[String] => B) = new LabelValues.`1`[B] {

      def labels(value: String) = f(List(value))
    }
  }

  implicit val withLabelValues2: WithLabelValues[LabelValues.`2`] = new WithLabelValues[LabelValues.`2`] {

    def apply[B](f: List[String] => B) = new LabelValues.`2`[B] {

      def labels(value1: String, value2: String) = f(List(value1, value2))
    }
  }

  implicit val withLabelValues3: WithLabelValues[LabelValues.`3`] = new WithLabelValues[LabelValues.`3`] {

    def apply[B](f: List[String] => B) = new LabelValues.`3`[B] {

      def labels(value1: String, value2: String, value3: String) = f(List(value1, value2, value3))
    }
  }

  implicit val withLabelValues4: WithLabelValues[LabelValues.`4`] = new WithLabelValues[LabelValues.`4`] {

    def apply[B](f: List[String] => B) = new LabelValues.`4`[B] {

      def labels(value1: String, value2: String, value3: String, value4: String) = {
        f(List(value1, value2, value3, value4))
      }
    }
  }

  implicit val withLabelValues5: WithLabelValues[LabelValues.`5`] = new WithLabelValues[LabelValues.`5`] {

    def apply[B](f: List[String] => B) = new LabelValues.`5`[B] {

      def labels(value1: String, value2: String, value3: String, value4: String, value5: String) = {
        f(List(value1, value2, value3, value4, value5))
      }
    }
  }

  implicit val withLabelValues6: WithLabelValues[LabelValues.`6`] = new WithLabelValues[LabelValues.`6`] {

    def apply[B](f: List[String] => B) = new LabelValues.`6`[B] {

      def labels(value1: String, value2: String, value3: String, value4: String, value5: String, value6: String) = {
        f(List(value1, value2, value3, value4, value5, value6))
      }
    }
  }

  implicit val withLabelValues7: WithLabelValues[LabelValues.`7`] = new WithLabelValues[LabelValues.`7`] {

    def apply[B](f: List[String] => B) = new LabelValues.`7`[B] {

      def labels(
          value1: String,
          value2: String,
          value3: String,
          value4: String,
          value5: String,
          value6: String,
          value7: String
      ) = {
        f(List(value1, value2, value3, value4, value5, value6, value7))
      }
    }
  }
}
