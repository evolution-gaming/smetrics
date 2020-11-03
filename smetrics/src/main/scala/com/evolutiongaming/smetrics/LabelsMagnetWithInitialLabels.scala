package com.evolutiongaming.smetrics


trait LabelsMagnetWithInitialLabels[A, B[_], D[_]] extends LabelsMagnet[A, B] {

  def withInitialLabelValues[F](f: List[List[String]] => F): D[F]
}

object LabelsMagnetWithInitialLabels {

  implicit val labelsMagnet0: LabelsMagnetWithInitialLabels[LabelNames.`0`.type, LabelValues.`0`, InitialLabelValues.`0`] = of[LabelNames.`0`.type, LabelValues.`0`, InitialLabelValues.`0`]


  implicit val labelsMagnet1: LabelsMagnetWithInitialLabels[LabelNames.`1`, LabelValues.`1`, InitialLabelValues.`1`] = of[LabelNames.`1`, LabelValues.`1`, InitialLabelValues.`1`]


  implicit val labelsMagnet2: LabelsMagnetWithInitialLabels[LabelNames.`2`, LabelValues.`2`, InitialLabelValues.`2`] = of[LabelNames.`2`, LabelValues.`2`, InitialLabelValues.`2`]


  implicit val labelsMagnet3: LabelsMagnetWithInitialLabels[LabelNames.`3`, LabelValues.`3`, InitialLabelValues.`3`] = of[LabelNames.`3`, LabelValues.`3`, InitialLabelValues.`3`]


  implicit val labelsMagnet4: LabelsMagnetWithInitialLabels[LabelNames.`4`, LabelValues.`4`, InitialLabelValues.`4`] = of[LabelNames.`4`, LabelValues.`4`, InitialLabelValues.`4`]


  implicit val labelsMagnet5: LabelsMagnetWithInitialLabels[LabelNames.`5`, LabelValues.`5`, InitialLabelValues.`5`] = of[LabelNames.`5`, LabelValues.`5`, InitialLabelValues.`5`]


  implicit val labelsMagnet6: LabelsMagnetWithInitialLabels[LabelNames.`6`, LabelValues.`6`, InitialLabelValues.`6`] = of[LabelNames.`6`, LabelValues.`6`, InitialLabelValues.`6`]


  implicit val labelsMagnet7: LabelsMagnetWithInitialLabels[LabelNames.`7`, LabelValues.`7`, InitialLabelValues.`7`] = of[LabelNames.`7`, LabelValues.`7`, InitialLabelValues.`7`]


  private def of[A <: LabelNames, B[_] : WithLabelValues, D[_] : WithInitialLabelValues]: LabelsMagnetWithInitialLabels[A, B, D] = new LabelsMagnetWithInitialLabels[A, B, D] {

    def names(a: A) = a.toList

    def withValues[C](f: List[String] => C): B[C] = WithLabelValues[B].apply(f)

    def withInitialLabelValues[F](f: List[List[String]] => F): D[F] = WithInitialLabelValues[D].apply(f)
  }
}
