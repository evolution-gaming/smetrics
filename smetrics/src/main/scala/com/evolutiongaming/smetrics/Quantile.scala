package com.evolutiongaming.smetrics

final case class Quantile(value: Double, error: Double)

final case class Quantiles(values: List[Quantile])

object Quantiles {

  val Empty: Quantiles = Quantiles(List.empty)

  val Default: Quantiles = Quantiles(Quantile(value = 0.9, error = 0.05), Quantile(value = 0.99, error = 0.005))

  def apply(values: Quantile*): Quantiles = Quantiles(values.toList)
}
