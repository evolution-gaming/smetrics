package com.evolutiongaming.smetrics

final case class Quantile(value: Double, error: Double)


final case class Quantiles(values: List[Quantile])

object Quantiles {

  val Empty: Quantiles = Quantiles(List.empty)

  def apply(values: Quantile*): Quantiles = Quantiles(values.toList)
}
