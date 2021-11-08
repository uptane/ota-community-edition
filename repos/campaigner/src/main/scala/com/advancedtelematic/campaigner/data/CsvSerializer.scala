package com.advancedtelematic.campaigner.data

import cats.Show
import cats.syntax.show._
import com.advancedtelematic.libats.data.DataType.{ResultCode, ResultDescription}

trait CsvSerializer[T] {
  def toCsvRow(value: T): Seq[String]
}

object CsvSerializer {
  val fieldSeparator = ";"
  val recordSeparator = "\n"

  implicit val showString: Show[String] = identity _
  implicit val showResultCode: Show[ResultCode] = _.value
  implicit val showResultDescription: Show[ResultDescription] = _.value

  implicit def tuple3Serializer[A: Show, B: Show, C: Show]: CsvSerializer[(A, B, C)] =
    (t: (A, B, C)) => t._1.show +: t._2.show +: t._3.show +: Nil

  implicit val deviceIdFailureSerializer = implicitly[CsvSerializer[(String, ResultCode, ResultDescription)]]

  def asCsv[T](header: Seq[String], rows: Seq[T])(implicit serializer: CsvSerializer[T]): String = {
    val head = header.mkString(fieldSeparator)
    val body = rows.map(serializer.toCsvRow(_).mkString(fieldSeparator))
    (head +: body).mkString(recordSeparator)
  }
}

