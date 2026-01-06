#!/usr/bin/env amm

import $ivy.`io.github.uptane::libtuf:3.0.0`

import io.circe.{Json, JsonObject}
import io.circe.syntax._
import java.nio.channels.Channels

import com.advancedtelematic.libtuf.crypt.CanonicalJson._

// Reads json from stdin and outputs canonical json
//
// Useful to calculate checksums:

// cat targets.json | amm canonical-json.sc | sha256sum

@main
def main() = {
  val ch = Channels.newChannel(System.in)

  val j = io.circe.jawn.parseChannel(ch).right.get.canonical

  print(j)
}
