package com.advancedtelematic.director.data

import io.circe.{Decoder, Encoder, Json}

// TODO: Remove after https://github.com/circe/circe/pull/983/files is in the new circe release
// TODO: Move to libats
object JsonDropNullValues {
  implicit class JsonDropNullValues(value: Json) {
    def dropNullValues: Json = value.mapObject(_.filter { case (_, v) => !v.isNull })

    private def rec(counter: Int)(json: Json): Json = {
      if (counter == 0)
        json
      else
        json.arrayOrObject[Json](
          json,
          array => Json.fromValues(array.map(rec(counter - 1))),
          obj => Json.fromJsonObject(obj.filter { case (_, v) => !v.isNull }.mapValues(rec(counter - 1)))
        )
    }

    // Drops values recursively, limited to 5 levels
    def dropNullValuesDeep: Json = rec(5)(value)
  }

  implicit class EncoderDropNullValues[T](value: Encoder[T]) {
    def dropNullValues: Encoder[T] = value.mapJson(_.dropNullValues)
  }
}
