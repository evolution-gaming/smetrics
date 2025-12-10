package com.evolutiongaming.smetrics

object LabelValues {

  type `0`[A] = A

  trait `1`[A] {

    def labels(value: String): A
  }

  trait `2`[A] {

    def labels(value1: String, value2: String): A
  }

  trait `3`[A] {

    def labels(value1: String, value2: String, value3: String): A
  }

  trait `4`[A] {

    def labels(
      value1: String,
      value2: String,
      value3: String,
      value4: String,
    ): A
  }

  trait `5`[A] {

    def labels(
      value1: String,
      value2: String,
      value3: String,
      value4: String,
      value5: String,
    ): A
  }

  trait `6`[A] {

    def labels(
      value1: String,
      value2: String,
      value3: String,
      value4: String,
      value5: String,
      value6: String,
    ): A
  }

  trait `7`[A] {

    def labels(
      value1: String,
      value2: String,
      value3: String,
      value4: String,
      value5: String,
      value6: String,
      value7: String,
    ): A
  }
}
