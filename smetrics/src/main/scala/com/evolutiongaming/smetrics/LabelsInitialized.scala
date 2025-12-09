package com.evolutiongaming.smetrics

import cats.data.{NonEmptyList => Nel}

trait LabelsInitialized {

  def names: List[String]

  def values: List[List[String]]
}

object LabelsInitialized {

  def apply(): `0`.type = `0`

  def apply(name: String, values: Nel[String]): `1` = `1`(name, values)

  def apply(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
  ): `2` = `2`(name1, values1, name2, values2)

  def apply(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
    name3: String,
    values3: Nel[String],
  ): `3` = `3`(name1, values1, name2, values2, name3, values3)

  def apply(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
    name3: String,
    values3: Nel[String],
    name4: String,
    values4: Nel[String],
  ): `4` = `4`(name1, values1, name2, values2, name3, values3, name4, values4)

  def apply(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
    name3: String,
    values3: Nel[String],
    name4: String,
    values4: Nel[String],
    name5: String,
    values5: Nel[String],
  ): `5` = `5`(name1, values1, name2, values2, name3, values3, name4, values4, name5, values5)

  def apply(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
    name3: String,
    values3: Nel[String],
    name4: String,
    values4: Nel[String],
    name5: String,
    values5: Nel[String],
    name6: String,
    values6: Nel[String],
  ): `6` = `6`(name1, values1, name2, values2, name3, values3, name4, values4, name5, values5, name6, values6)

  def apply(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
    name3: String,
    values3: Nel[String],
    name4: String,
    values4: Nel[String],
    name5: String,
    values5: Nel[String],
    name6: String,
    values6: Nel[String],
    name7: String,
    values7: Nel[String],
  ): `7` =
    `7`(name1, values1, name2, values2, name3, values3, name4, values4, name5, values5, name6, values6, name7, values7)

  case object `0` extends LabelsInitialized {

    def names: List[String] = List.empty

    def values: List[List[String]] = List.empty

    def add(name: String, values: Nel[String]): `1` = `1`(name, values)
  }

  final case class `1`(name1: String, values1: Nel[String]) extends LabelsInitialized {

    def names: List[String] = List(name1)

    def values: List[List[String]] = List(values1.toList)

    def add(name: String, values: Nel[String]): `2` = `2`(name1, values1, name, values)
  }

  final case class `2`(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
  ) extends LabelsInitialized {

    def names: List[String] = List(name1, name2)

    def values: List[List[String]] = List(values1.toList, values2.toList)

    def add(name: String, values: Nel[String]): `3` = `3`(name1, values1, name2, values2, name, values)
  }

  final case class `3`(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
    name3: String,
    values3: Nel[String],
  ) extends LabelsInitialized {

    def names: List[String] = List(name1, name2, name3)

    def values: List[List[String]] = List(values1.toList, values2.toList, values3.toList)

    def add(name: String, values: Nel[String]): `4` =
      `4`(name1, values1, name2, values2, name3, values3, name, values)
  }

  final case class `4`(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
    name3: String,
    values3: Nel[String],
    name4: String,
    values4: Nel[String],
  ) extends LabelsInitialized {

    def names: List[String] = List(name1, name2, name3, name4)

    def values: List[List[String]] =
      List(values1.toList, values2.toList, values3.toList, values4.toList)

    def add(name: String, values: Nel[String]): `5` =
      `5`(name1, values1, name2, values2, name3, values3, name4, values4, name, values)
  }

  final case class `5`(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
    name3: String,
    values3: Nel[String],
    name4: String,
    values4: Nel[String],
    name5: String,
    values5: Nel[String],
  ) extends LabelsInitialized {

    def names: List[String] = List(name1, name2, name3, name4, name5)

    def values: List[List[String]] =
      List(values1.toList, values2.toList, values3.toList, values4.toList, values5.toList)

    def add(name: String, values: Nel[String]): `6` =
      `6`(name1, values1, name2, values2, name3, values3, name4, values4, name5, values5, name, values)
  }

  final case class `6`(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
    name3: String,
    values3: Nel[String],
    name4: String,
    values4: Nel[String],
    name5: String,
    values5: Nel[String],
    name6: String,
    values6: Nel[String],
  ) extends LabelsInitialized {

    def names: List[String] = List(name1, name2, name3, name4, name5, name6)

    def values: List[List[String]] =
      List(values1.toList, values2.toList, values3.toList, values4.toList, values5.toList, values6.toList)

    def add(name: String, values: Nel[String]): `7` =
      `7`(name1, values1, name2, values2, name3, values3, name4, values4, name5, values5, name6, values6, name, values)
  }

  final case class `7`(
    name1: String,
    values1: Nel[String],
    name2: String,
    values2: Nel[String],
    name3: String,
    values3: Nel[String],
    name4: String,
    values4: Nel[String],
    name5: String,
    values5: Nel[String],
    name6: String,
    values6: Nel[String],
    name7: String,
    values7: Nel[String],
  ) extends LabelsInitialized {

    def names: List[String] = List(name1, name2, name3, name4, name5, name6, name7)

    def values: List[List[String]] =
      List(
        values1.toList,
        values2.toList,
        values3.toList,
        values4.toList,
        values5.toList,
        values6.toList,
        values7.toList,
      )
  }
}
