package com.evolutiongaming.smetrics


trait LabelNames {

  def toList: List[String]
}

object LabelNames {

  def apply(): `0`.type = `0`

  def apply(name: String): `1` = `1`(name)

  def apply(name1: String, name2: String): `2` = `2`(name1, name2)

  def apply(name1: String, name2: String, name3: String): `3` = `3`(name1, name2, name3)

  def apply(name1: String, name2: String, name3: String, name4: String): `4` = `4`(name1, name2, name3, name4)

  def apply(name1: String, name2: String, name3: String, name4: String, name5: String): `5` = `5`(name1, name2, name3, name4, name5)

  def apply(name1: String, name2: String, name3: String, name4: String, name5: String, name6: String): `6` = `6`(name1, name2, name3, name4, name5, name6)

  def apply(name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String): `7` = `7`(name1, name2, name3, name4, name5, name6, name7)


  case object `0` extends LabelNames {

    def toList = List.empty
  }


  final case class `1`(name1: String) extends LabelNames {

    def toList = List(name1)
  }


  final case class `2`(name1: String, name2: String) extends LabelNames {

    def toList = List(name1, name2)
  }


  final case class `3`(name1: String, name2: String, name3: String) extends LabelNames {

    def toList = List(name1, name2, name3)
  }


  final case class `4`(name1: String, name2: String, name3: String, name4: String) extends LabelNames {

    def toList = List(name1, name2, name3, name4)
  }


  final case class `5`(name1: String, name2: String, name3: String, name4: String, name5: String) extends LabelNames {

    def toList = List(name1, name2, name3, name4, name5)
  }


  final case class `6`(name1: String, name2: String, name3: String, name4: String, name5: String, name6: String) extends LabelNames {

    def toList = List(name1, name2, name3, name4, name5, name6)
  }


  final case class `7`(name1: String, name2: String, name3: String, name4: String, name5: String, name6: String, name7: String) extends LabelNames {

    def toList = List(name1, name2, name3, name4, name5, name6, name7)
  }
}
