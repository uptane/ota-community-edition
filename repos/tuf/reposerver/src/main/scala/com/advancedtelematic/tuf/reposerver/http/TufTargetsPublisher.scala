package com.advancedtelematic.tuf.reposerver.http

import com.advancedtelematic.libats.data.DataType.{Checksum, Namespace}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libtuf.data.ClientDataType.{ClientTargetItem, TargetCustom}
import com.advancedtelematic.libtuf.data.TufDataType.TargetFilename
import com.advancedtelematic.libtuf_server.data.Messages.TufTargetAdded
import cats.implicits._

import scala.concurrent.{ExecutionContext, Future}
import com.advancedtelematic.libats.codecs.CirceCodecs._
import com.advancedtelematic.libtuf.data.ClientCodecs._
import com.advancedtelematic.tuf.reposerver.data.RepositoryDataType.TargetItem


class TufTargetsPublisher(messageBus: MessageBusPublisher)(implicit ec: ExecutionContext) {
  def targetAdded(namespace: Namespace, item: TargetItem): Future[Unit] =
    messageBus.publish(TufTargetAdded(namespace, item.filename, item.checksum, item.length, item.custom))

  def newTargetsAdded(namespace: Namespace, allTargets: Map[TargetFilename, ClientTargetItem], existing: Seq[TargetItem]): Future[Unit] = {
    newTargetsFromExisting(allTargets, existing.map(_.filename)).toList.traverse_ { case (filename, checksum, clientTargetItem) =>
      messageBus.publish(TufTargetAdded(namespace, filename, checksum,
                                        clientTargetItem.length, clientTargetItem.customParsed[TargetCustom]))
    }
  }

  private def newTargetsFromExisting(allTargets: Map[TargetFilename, ClientTargetItem], existing: Seq[TargetFilename]) =
    (allTargets -- existing.toSet).flatMap { case (targetFilename, clientTargetItem) =>
      clientTargetItem.hashes.headOption.map { case (hashMethod, validChecksum) =>
        (targetFilename, Checksum(hashMethod, validChecksum), clientTargetItem)
      }
    }
}
