package com.advancedtelematic.director.daemon

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.director.data.DbDataType.{Assignment, AutoUpdateDefinition, EcuTarget, EcuTargetId}
import com.advancedtelematic.director.db.{AssignmentsRepositorySupport, AutoUpdateDefinitionRepositorySupport, DeviceRepositorySupport, EcuTargetsRepositorySupport}
import com.advancedtelematic.libats.data.DataType.{AutoUpdateId, Namespace}
import com.advancedtelematic.libats.messaging.MsgOperation.MsgOperation
import com.advancedtelematic.libtuf_server.data.Messages.TufTargetAdded
import org.mariadb.jdbc.internal.logging.LoggerFactory
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class TufTargetAddedListener()(implicit val db: Database, val ec: ExecutionContext)
  extends MsgOperation[TufTargetAdded]
    with AutoUpdateDefinitionRepositorySupport
    with AssignmentsRepositorySupport
    with EcuTargetsRepositorySupport
    with DeviceRepositorySupport {

  import scala.async.Async._

  private def ecuTargetFromTufTarget(msg: TufTargetAdded): Future[EcuTarget] = {
    val ecuTargetId = EcuTargetId.generate()
    val ecuTarget = EcuTarget(msg.namespace, ecuTargetId, msg.filename,
      msg.length, msg.checksum, msg.checksum.hash, msg.custom.flatMap(_.uri.map(u => Uri(u.toString))))
    // TODO: Reuse ecuTarget if one exists with checksum/length, etc
    FastFuture.successful(ecuTarget)
  }

  private val _log = LoggerFactory.getLogger(this.getClass)

  private def filterDevicesWithExistingAssignments(assignments: List[Assignment]): Future[List[Assignment]] = async {
    val assignmentExists = await(assignmentsRepository.existsForDevices(assignments.map(_.deviceId).toSet)).filter(_._2).keys.toSet

    assignmentExists.foreach { d =>
      _log.info(s"Not creating auto update assignment for $d, assignment for that device already exists")
    }

    assignments.filterNot(a => assignmentExists.contains(a.deviceId))
  }

  private def createAssignments(ns: Namespace,
                                ecuTarget: EcuTarget,
                                autoUpdateDefinitions: List[AutoUpdateDefinition]): Future[Unit] = async {

    val assignments = autoUpdateDefinitions.map { autoUpdate =>
      Assignment(ns, autoUpdate.deviceId, autoUpdate.ecuId, ecuTarget.id, AutoUpdateId(autoUpdate.id.uuid), inFlight = false)
    }

    val newAssignments = await(filterDevicesWithExistingAssignments(assignments))

    _log.info(s"Creating auto update assignments for ${newAssignments.length} devices")

    newAssignments.foreach { assignment =>
      _log.info(s"Creating auto update assignment for ${assignment.deviceId} (target = ${ecuTarget.filename})")
    }

    assignmentsRepository.persistManyForEcuTarget(ecuTargetsRepository, deviceRepository)(ecuTarget, newAssignments)
  }

  override def apply(msg: TufTargetAdded): Future[_] = async {
    msg.custom.map(_.name) match {
      case Some(name) =>
        val updateDefinitions = await(autoUpdateDefinitionRepository.findByName(msg.namespace, name)).toList
        val ecuTarget = await(ecuTargetFromTufTarget(msg))

        await(createAssignments(msg.namespace, ecuTarget, updateDefinitions))
      case None =>
        _log.debug("No auto update found, package has no target name defined")
    }
  }
}
