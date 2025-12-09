package com.evolutiongaming.smetrics

import cats.data.NonEmptyList as Nel

import scala.annotation.tailrec

final case class Buckets(values: Nel[Double])

object Buckets {

  def linear(start: Double, width: Double, count: Int): Buckets = {

    @tailrec def loop(buckets: Nel[Double]): Nel[Double] = {
      val size = buckets.size
      if (size >= count) buckets
      else {
        val bucket = start + size * width
        loop(bucket :: buckets)
      }
    }

    val buckets = loop(Nel.one(start))
    Buckets(buckets.reverse)
  }

  def exponential(start: Double, factor: Double, count: Int): Buckets = {

    @tailrec def loop(buckets: Nel[Double]): Nel[Double] = {
      val size = buckets.size
      if (size >= count) buckets
      else {
        val bucket = start * Math.pow(factor, size.toDouble)
        loop(bucket :: buckets)
      }
    }

    val buckets = loop(Nel.one(start))
    Buckets(buckets.reverse)
  }
}
