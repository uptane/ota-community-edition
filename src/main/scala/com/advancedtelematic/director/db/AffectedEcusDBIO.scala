package com.advancedtelematic.director.db

import com.advancedtelematic.director.data.DataType.TargetSpecId
import com.advancedtelematic.director.data.DbDataType.{Ecu, EcuTarget, EcuTargetId, HardwareUpdate}
import com.advancedtelematic.director.http.DeviceAssignments.AffectedEcusResult
import com.advancedtelematic.director.http.Errors
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.*
import com.advancedtelematic.libats.messaging_datatype.DataType.ValidEcuIdentifier
import com.advancedtelematic.libtuf.data.TufDataType.HardwareIdentifier
import eu.timepit.refined.refineV
import io.circe.syntax.EncoderOps
import org.slf4j.LoggerFactory
import slick.dbio.DBIO
import slick.jdbc.MySQLProfile.api.*
import scala.concurrent.{ExecutionContext, Future}

class AffectedEcusDBIO()(implicit val db: Database, val ec: ExecutionContext)
    extends HardwareUpdateRepositorySupport
    with EcuTargetsRepositorySupport
    with EcuRepositorySupport
    with UpdatesRepositorySupport
    with AssignmentsRepositorySupport {

  private val _log = LoggerFactory.getLogger(this.getClass)

  def findAffectedEcus(ns: Namespace,
                       devices: Seq[DeviceId],
                       targetSpecId: TargetSpecId): Future[AffectedEcusResult] =
    db.run(findAffectedEcusAction(ns, devices, targetSpecId).transactionally)

  protected[db] def findAffectedEcusAction(
    ns: Namespace,
    devices: Seq[DeviceId],
    targetSpecId: TargetSpecId,
    ignoreScheduledUpdates: Boolean = false): DBIO[AffectedEcusResult] =
    for {
      // Find hardware updates and their targets
      (hardwareUpdates, allTargets) <- findHardwareUpdatesAndTargets(ns, targetSpecId)

      // Filter devices with compatible hardware
      (compatible, unaffected) <- filterDevicesWithCompatibleHardware(
        ns,
        devices,
        hardwareUpdates,
        targetSpecId
      )

      // Filter out devices with scheduled updates
      (compatible, unaffected) <-
        if (ignoreScheduledUpdates)
          DBIO.successful((compatible, unaffected))
        else
          filterDevicesWithActiveUpdates(ns, compatible, unaffected, targetSpecId)

      // Determine which ECUs are affected by the update
      affected = determineAffectedEcus(
        compatible,
        hardwareUpdates,
        allTargets,
        unaffected,
        targetSpecId
      )

      // Filter out ECUs with running assignments
      finalResult <- filterEcusWithRunningAssignments(ns, affected)
    } yield finalResult

  private def findHardwareUpdatesAndTargets(ns: Namespace, targetSpecId: TargetSpecId)
    : DBIO[(Map[HardwareIdentifier, HardwareUpdate], Map[EcuTargetId, EcuTarget])] =
    for {
      hardwareUpdates <- hardwareUpdateRepository.findByAction(ns, targetSpecId)
      allTargetIds = hardwareUpdates.values.flatMap { update =>
        Seq(update.toTarget) ++ update.fromTarget.toSeq
      }
      allTargets <- ecuTargetsRepository.findAllAction(ns, allTargetIds.toSeq)
    } yield (hardwareUpdates, allTargets)

  private def filterDevicesWithCompatibleHardware(
    ns: Namespace,
    devices: Seq[DeviceId],
    hardwareUpdates: Map[HardwareIdentifier, HardwareUpdate],
    targetSpecId: TargetSpecId): DBIO[(Seq[(Ecu, Option[EcuTarget])], AffectedEcusResult)] =
    for {
      ecusWithCompatibleHardware <- ecuRepository.findEcuWithTargetsAction(
        devices.toSet,
        hardwareUpdates.keys.toSet
      )

      // Find devices with incompatible hardware
      devicesWithIncompatibleHardware = devices.toSet -- ecusWithCompatibleHardware
        .map(_._1.deviceId)
        .toSet

      // Get primary ECU IDs for devices with incompatible hardware
      devicePrimaries <- ecuRepository.findDevicePrimaryIdsAction(
        ns,
        devicesWithIncompatibleHardware
      )

      // Create result for devices with incompatible hardware
      unaffectedDueToHardware = devicesWithIncompatibleHardware.foldLeft(
        AffectedEcusResult(Seq.empty, Map.empty)
      ) { case (acc, deviceId) =>
        val error = Errors.DeviceNoCompatibleHardware(deviceId, targetSpecId)
        _log.info(error.getMessage)
        val primaryEcuId =
          devicePrimaries.getOrElse(deviceId, refineV[ValidEcuIdentifier].unsafeFrom("unknown"))
        acc.addNotAffected(deviceId, primaryEcuId, error)
      }
    } yield (ecusWithCompatibleHardware, unaffectedDueToHardware)

  private def filterDevicesWithActiveUpdates(
    ns: Namespace,
    ecusWithCompatibleHardware: Seq[(Ecu, Option[EcuTarget])],
    unaffectedDueToHardware: AffectedEcusResult,
    targetSpecId: TargetSpecId): DBIO[(Seq[(Ecu, Option[EcuTarget])], AffectedEcusResult)] =
    for {
      devicesWithUpdates <- updatesRepository.filterActiveUpdateExistsAction(
        ns,
        ecusWithCompatibleHardware.map(_._1.deviceId).toSet
      )

      _ = _log
        .atDebug()
        .addKeyValue("devicesWithActiveUpdates", devicesWithUpdates.asJson)
        .log()

      ecusWithoutScheduledUpdates = ecusWithCompatibleHardware.filterNot { case (ecu, _) =>
        devicesWithUpdates.contains(ecu.deviceId)
      }

      unaffectedDueToActiveUpdates = devicesWithUpdates.foldLeft(unaffectedDueToHardware) {
        case (acc, deviceId) =>
          val ecus = ecusWithCompatibleHardware.filter(_._1.deviceId == deviceId)

          ecus.foldLeft(acc) { case (result, (ecu, _)) =>
            val error = Errors.DeviceHasActiveUpdate(deviceId, targetSpecId)
            _log.info(error.getMessage)
            result.addNotAffected(deviceId, ecu.ecuSerial, error)
          }
      }
    } yield (ecusWithoutScheduledUpdates, unaffectedDueToActiveUpdates)

  private def determineAffectedEcus(compatibleEcus: Seq[(Ecu, Option[EcuTarget])],
                                    hardwareUpdates: Map[HardwareIdentifier, HardwareUpdate],
                                    allTargets: Map[EcuTargetId, EcuTarget],
                                    notAffected: AffectedEcusResult,
                                    targetSpecId: TargetSpecId): AffectedEcusResult =

    compatibleEcus.foldLeft(notAffected) { case (acc, (ecu, installedTarget)) =>
      val hwUpdate = hardwareUpdates(ecu.hardwareId)
      val updateFrom = hwUpdate.fromTarget.flatMap(allTargets.get)
      val updateTo = allTargets(hwUpdate.toTarget)

      if (isEcuUpdateable(hwUpdate, installedTarget, updateFrom)) {
        if (isTargetAlreadyInstalled(installedTarget, updateTo)) {
          val error = Errors.InstalledTargetIsUpdate(ecu.deviceId, ecu.ecuSerial, hwUpdate)
          _log.info(error.getMessage)
          acc.addNotAffected(ecu.deviceId, ecu.ecuSerial, error)
        } else {
          _log.info(s"${ecu.deviceId}/${ecu.ecuSerial} affected for $hwUpdate")
          acc.addAffected(ecu, hwUpdate.toTarget)
        }
      } else {
        val error = Errors.NotAffectedByMtu(ecu.deviceId, ecu.ecuSerial, targetSpecId)
        _log.info(error.getMessage)
        acc.addNotAffected(ecu.deviceId, ecu.ecuSerial, error)
      }
    }

  private def isEcuUpdateable(hwUpdate: HardwareUpdate,
                              installedTarget: Option[EcuTarget],
                              updateFrom: Option[EcuTarget]): Boolean =
    hwUpdate.fromTarget.isEmpty || installedTarget.zip(updateFrom).exists { case (a, b) =>
      a.matches(b)
    }

  private def isTargetAlreadyInstalled(installedTarget: Option[EcuTarget],
                                       updateTo: EcuTarget): Boolean =
    installedTarget.exists(_.matches(updateTo))

  private def filterEcusWithRunningAssignments(
    ns: Namespace,
    ecusResult: AffectedEcusResult): DBIO[AffectedEcusResult] = {
    val ecuIds = ecusResult.affected.map { case (ecu, _) =>
      ecu.deviceId -> ecu.ecuSerial
    }.toSet

    assignmentsRepository.withAssignmentsAction(ns, ecuIds).map { ecusWithAssignments =>
      ecusResult.affected.foldLeft(AffectedEcusResult(Seq.empty, ecusResult.notAffected)) {
        case (acc, (ecu, _)) if ecusWithAssignments.contains(ecu.deviceId -> ecu.ecuSerial) =>
          val error = Errors.NotAffectedRunningAssignment(ecu.deviceId, ecu.ecuSerial)
          _log.info(error.getMessage)
          acc.addNotAffected(ecu.deviceId, ecu.ecuSerial, error)
        case (acc, (ecu, target)) =>
          acc.addAffected(ecu, target)
      }
    }
  }

}
