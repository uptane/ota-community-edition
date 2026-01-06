package com.advancedtelematic.treehub.http

class ConfResource {

  import akka.http.scaladsl.server.Directives._

  val route = path("config") {

    // `indexed-deltas` is from https://github.com/ostreedev/ostree/blob/main/src/libostree/ostree-repo-pull.c#L4041C73-L4041C87

    val c =
      """
        |[core]
        |repo_version=1
        |mode=archive-z2
        |indexed-deltas=true
        |
      """.stripMargin

    complete(c)
  }
}
