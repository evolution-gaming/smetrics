package com.evolutiongaming.smetrics

trait InitializedLabelsMagnet[A, B[_]] extends LabelsMagnet[A, B] {

  def values(a: A): List[List[String]]
}

object InitializedLabelsMagnet {

  implicit val labelsMagnet0: InitializedLabelsMagnet[InitializedLabels.`0`.type, LabelValues.`0`] = of[InitializedLabels.`0`.type, LabelValues.`0`]


  implicit val labelsMagnet1: InitializedLabelsMagnet[InitializedLabels.`1`, LabelValues.`1`] = of[InitializedLabels.`1`, LabelValues.`1`]


  implicit val labelsMagnet2: InitializedLabelsMagnet[InitializedLabels.`2`, LabelValues.`2`] = of[InitializedLabels.`2`, LabelValues.`2`]


  implicit val labelsMagnet3: InitializedLabelsMagnet[InitializedLabels.`3`, LabelValues.`3`] = of[InitializedLabels.`3`, LabelValues.`3`]


  implicit val labelsMagnet4: InitializedLabelsMagnet[InitializedLabels.`4`, LabelValues.`4`] = of[InitializedLabels.`4`, LabelValues.`4`]


  implicit val labelsMagnet5: InitializedLabelsMagnet[InitializedLabels.`5`, LabelValues.`5`] = of[InitializedLabels.`5`, LabelValues.`5`]


  implicit val labelsMagnet6: InitializedLabelsMagnet[InitializedLabels.`6`, LabelValues.`6`] = of[InitializedLabels.`6`, LabelValues.`6`]


  implicit val labelsMagnet7: InitializedLabelsMagnet[InitializedLabels.`7`, LabelValues.`7`] = of[InitializedLabels.`7`, LabelValues.`7`]


  private def of[A <: InitializedLabels, B[_] : WithLabelValues]: InitializedLabelsMagnet[A, B] = new InitializedLabelsMagnet[A, B] {

    def names(a: A) = a.names

    def values(a: A): List[List[String]] = a.values

    def withValues[C](f: List[String] => C) = WithLabelValues[B].apply(f)
  }
}
