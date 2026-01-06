import java.util.HexFormat
import os._
import os.Path._

import $ivy.`com.lihaoyi::requests:0.8.0`
import requests.RequestBlob
import java.security.MessageDigest

def uploadObjects(host: String, ns: String, repo: Path) = {
  os.walk.attrs(repo / "objects").foreach { case (p, attr) =>
    if (attr.isFile) {
      println(p)

      val status = requests
        .post(
          s"http://$host/api/v3/objects/${p.relativeTo(repo / "objects")}",
          data = p.toIO,
          headers = List("x-ats-namespace" -> ns),
        )
        .statusCode

      println(s"Finished: $status")
    }
  }
}

def uploadDelta(host: String, ns: String, repo: Path, deltaId: RelPath) = {
  val deltasDir = repo / "deltas"
  val deltaPath = deltasDir / deltaId

  val superblockHash = {
    val path = deltaPath / "superblock"

    val sha = MessageDigest
      .getInstance("SHA-256")
      .digest(os.read.bytes(path))
      .map("%02x".format(_))
      .mkString

    sha
  }

  println(s"sha is $superblockHash")

  os.walk.attrs(deltaPath).sortBy(_._1.baseName).foreach { case (p, attr) =>
    println(p)

    val status = requests
      .post(
        s"http://$host/api/v3/deltas/${p.relativeTo(deltasDir)}",
        data = p.toIO,
        headers = List("x-trx-superblock-hash" -> superblockHash, "x-ats-namespace" -> ns),
      )
      .statusCode

    println(s"Finished: $status")
  }
}

@main
def main(cmd: String, repopath: String, deltaId: Option[String] = None) = {
  val host = sys.env.get("TREEHUBCLI_HOST").getOrElse("localhost:9001")
  val ns = sys.env.get("TREEHUBCLI_NAMESPACE").getOrElse("default")

  if (cmd == "objects") {
    uploadObjects(host, ns, os.pwd / RelPath(repopath))
  } else if (cmd == "deltas" && deltaId.isDefined) {
    uploadDelta(host, ns, os.pwd / RelPath(repopath), RelPath(deltaId.get))
  } else {
    throw new IllegalArgumentException(
      "usage: treehub-cli.sc <objects|delta> <repopath> [delta-id]"
    )
  }
}
