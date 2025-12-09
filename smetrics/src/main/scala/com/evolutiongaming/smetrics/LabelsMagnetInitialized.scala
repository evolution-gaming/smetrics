package com.evolutiongaming.smetrics

trait LabelsMagnetInitialized[A, B[_]] extends LabelsMagnet[A, B] {

  def values(a: A): List[List[String]]
}

object LabelsMagnetInitialized {

  implicit val labelsMagnet0: LabelsMagnetInitialized[LabelsInitialized.`0`.type, LabelValues.`0`] =
    of[LabelsInitialized.`0`.type, LabelValues.`0`]

  implicit val labelsMagnet1: LabelsMagnetInitialized[LabelsInitialized.`1`, LabelValues.`1`] =
    of[LabelsInitialized.`1`, LabelValues.`1`]

  implicit val labelsMagnet2: LabelsMagnetInitialized[LabelsInitialized.`2`, LabelValues.`2`] =
    of[LabelsInitialized.`2`, LabelValues.`2`]

  implicit val labelsMagnet3: LabelsMagnetInitialized[LabelsInitialized.`3`, LabelValues.`3`] =
    of[LabelsInitialized.`3`, LabelValues.`3`]

  implicit val labelsMagnet4: LabelsMagnetInitialized[LabelsInitialized.`4`, LabelValues.`4`] =
    of[LabelsInitialized.`4`, LabelValues.`4`]

  implicit val labelsMagnet5: LabelsMagnetInitialized[LabelsInitialized.`5`, LabelValues.`5`] =
    of[LabelsInitialized.`5`, LabelValues.`5`]

  implicit val labelsMagnet6: LabelsMagnetInitialized[LabelsInitialized.`6`, LabelValues.`6`] =
    of[LabelsInitialized.`6`, LabelValues.`6`]

  implicit val labelsMagnet7: LabelsMagnetInitialized[LabelsInitialized.`7`, LabelValues.`7`] =
    of[LabelsInitialized.`7`, LabelValues.`7`]

  private def of[A <: LabelsInitialized, B[_]: WithLabelValues]: LabelsMagnetInitialized[A, B] =
    new LabelsMagnetInitialized[A, B] {

      def names(a: A) = a.names

      def values(a: A): List[List[String]] = a.values

      def withValues[C](f: List[String] => C): B[C] = WithLabelValues[B].apply(f)
    }
}
