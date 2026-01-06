package com.advancedtelematic.libtuf.crypt

import io.circe.{Json, JsonObject}
import io.circe.syntax._

object CanonicalJson {

  implicit class ToCanonicalJsonOps(value: Json) {
    def canonical: String = generate(value).noSpaces
  }

  private def generate(value: Json): Json =
    value.arrayOrObject[Json](
      value,
      array => Json.fromValues(array.map(generate)),
      obj =>
        JsonObject.fromIterable {
          obj.toList
            .map { case (k, v) =>
              k -> generate(v)
            }
            .sortBy(_._1)
        }.asJson
    )

}
