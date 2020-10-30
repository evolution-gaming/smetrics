package com.evolutiongaming.smetrics

import cats.data.{NonEmptyList => Nel}


object InitialLabelValues {

  type `0`[A] = A


  trait `1`[A] {

    def labelValues(values: Nel[String]): A
  }


  trait `2`[A] {

    def labelValues(values1: Nel[String], values2: Nel[String]): A
  }


  trait `3`[A] {

    def labelValues(values1: Nel[String], values2: Nel[String], values3: Nel[String]): A
  }


  trait `4`[A] {

    def labelValues(values1: Nel[String], values2: Nel[String], values3: Nel[String], values4: Nel[String]): A
  }


  trait `5`[A] {

    def labelValues(values1: Nel[String], values2: Nel[String], values3: Nel[String], values4: Nel[String], values5: Nel[String]): A
  }


  trait `6`[A] {

    def labelValues(values1: Nel[String], values2: Nel[String], values3: Nel[String], values4: Nel[String], values5: Nel[String], values6: Nel[String]): A
  }


  trait `7`[A] {

    def labelValues(values1: Nel[String], values2: Nel[String], values3: Nel[String], values4: Nel[String], values5: Nel[String], values6: Nel[String], values7: Nel[String]): A
  }
}