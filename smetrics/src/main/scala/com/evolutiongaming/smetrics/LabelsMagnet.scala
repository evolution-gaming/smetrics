package com.evolutiongaming.smetrics

trait LabelsMagnet[A, B[_]] {

  def names(a: A): List[String]

  def withValues[C](f: List[String] => C): B[C]
}

object LabelsMagnet {

  implicit val labelsMagnet0: LabelsMagnet[LabelNames.`0`.type, LabelValues.`0`] = of[LabelNames.`0`.type, LabelValues.`0`]


  implicit val labelsMagnet1: LabelsMagnet[LabelNames.`1`, LabelValues.`1`] = of[LabelNames.`1`, LabelValues.`1`]


  implicit val labelsMagnet2: LabelsMagnet[LabelNames.`2`, LabelValues.`2`] = of[LabelNames.`2`, LabelValues.`2`]


  implicit val labelsMagnet3: LabelsMagnet[LabelNames.`3`, LabelValues.`3`] = of[LabelNames.`3`, LabelValues.`3`]


  implicit val labelsMagnet4: LabelsMagnet[LabelNames.`4`, LabelValues.`4`] = of[LabelNames.`4`, LabelValues.`4`]


  implicit val labelsMagnet5: LabelsMagnet[LabelNames.`5`, LabelValues.`5`] = of[LabelNames.`5`, LabelValues.`5`]


  implicit val labelsMagnet6: LabelsMagnet[LabelNames.`6`, LabelValues.`6`] = of[LabelNames.`6`, LabelValues.`6`]


  implicit val labelsMagnet7: LabelsMagnet[LabelNames.`7`, LabelValues.`7`] = of[LabelNames.`7`, LabelValues.`7`]


  private def of[A <: LabelNames, B[_] : WithLabelValues]: LabelsMagnet[A, B] = new LabelsMagnet[A, B] {

    def names(a: A) = a.toList

    def withValues[C](f: List[String] => C) = WithLabelValues[B].apply(f)
  }
}