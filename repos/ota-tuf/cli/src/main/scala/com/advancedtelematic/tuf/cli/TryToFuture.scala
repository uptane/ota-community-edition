package com.advancedtelematic.tuf.cli

import scala.concurrent.Future
import scala.util.Try

object TryToFuture {

  implicit class TryToFutureOp[T](value: Try[T]) {
    def toFuture: Future[T] = Future.fromTry(value)
  }

}
