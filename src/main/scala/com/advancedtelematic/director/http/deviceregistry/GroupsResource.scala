/*
 * Copyright (c) 2017 ATS Advanced Telematic Systems GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.advancedtelematic.director.http.deviceregistry

import akka.http.scaladsl.marshalling.Marshaller.*
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.*

import akka.http.scaladsl.unmarshalling.{FromStringUnmarshaller, Unmarshaller}
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import akka.stream.scaladsl.Framing.FramingException
import akka.stream.scaladsl.{Framing, Sink, Source}
import akka.util.ByteString
import cats.syntax.either.*
import com.advancedtelematic.director.db.deviceregistry.{
  DeviceRepository,
  GroupInfoRepository,
  GroupMemberRepository
}
import com.advancedtelematic.director.deviceregistry.data.*
import com.advancedtelematic.director.deviceregistry.data.Codecs.*
import com.advancedtelematic.director.deviceregistry.data.DataType.UpdateHibernationStatusRequest
import com.advancedtelematic.director.deviceregistry.data.Device.DeviceOemId
import com.advancedtelematic.director.deviceregistry.data.DeviceStatus.DeviceStatus
import com.advancedtelematic.director.deviceregistry.data.Group.GroupId
import com.advancedtelematic.director.deviceregistry.data.GroupSortBy.GroupSortBy
import com.advancedtelematic.director.deviceregistry.data.GroupType.GroupType
import com.advancedtelematic.director.deviceregistry.{AllowUUIDPath, GroupMembership}
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport.*
import io.circe.{Codec, Decoder, Encoder, Json, KeyDecoder, KeyEncoder}
import slick.jdbc.MySQLProfile.api.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class LastSeenTable(`10mins`: Long,
                         `1hour`: Long,
                         `1day`: Long,
                         `1week`: Long,
                         `1month`: Long,
                         `1year`: Long)

case class DeviceGroupStats(status: Map[DeviceStatus, Long], lastSeen: LastSeenTable)

object DeviceGroupStats {
  implicit val deviceGroupStatusKeyEncoder: KeyEncoder[DeviceStatus] = KeyEncoder(_.toString)

  implicit val deviceGroupStatusKeyDecoder: KeyDecoder[DeviceStatus] =
    KeyDecoder.instance(str => Try(DeviceStatus.withName(str)).toOption)

  implicit val lastSeenTableCodec: Codec[LastSeenTable] =
    io.circe.generic.semiauto.deriveCodec[LastSeenTable]

  implicit val deviceGroupStatsCodec: Codec[DeviceGroupStats] =
    io.circe.generic.semiauto.deriveCodec[DeviceGroupStats]

}

import Unmarshallers.nonNegativeLong
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import com.advancedtelematic.libats.http.UUIDKeyAkka.*
import GroupId.*
import io.circe.syntax.*

class GroupsResource(namespaceExtractor: Directive1[Namespace],
                     deviceNamespaceAuthorizer: Directive1[DeviceId])(
  implicit ec: ExecutionContext,
  db: Database,
  materializer: Materializer)
    extends Directives {

  private val DEVICE_OEM_ID_MAX_BYTES = 128
  private val FILTER_EXISTING_DEVICES_BATCH_SIZE = 50

  private val GroupIdPath = {
    def groupAllowed(groupId: GroupId): Future[Namespace] =
      db.run(GroupInfoRepository.groupInfoNamespace(groupId))
    AllowUUIDPath(GroupId)(namespaceExtractor, groupAllowed)
  }

  implicit val deviceIdUnmarshaller: Unmarshaller[String, DeviceId] = DeviceId.unmarshaller
  implicit val groupIdUnmarshaller: Unmarshaller[String, GroupId] = GroupId.unmarshaller

  implicit val groupTypeUnmarshaller: FromStringUnmarshaller[GroupType] =
    Unmarshaller.strict(GroupType.withName)

  implicit val groupNameUnmarshaller: FromStringUnmarshaller[GroupName] =
    Unmarshaller.strict(GroupName.validatedGroupName.from(_).valueOr(throw _))

  implicit val sortByUnmarshaller: FromStringUnmarshaller[GroupSortBy] = Unmarshaller.strict {
    _.toLowerCase match {
      case "name"      => GroupSortBy.Name
      case "createdat" => GroupSortBy.CreatedAt
      case s => throw new IllegalArgumentException(s"Invalid value for sorting parameter: '$s'.")
    }
  }

  val groupMembership = new GroupMembership()

  def getDevicesInGroup(groupId: GroupId): Route =
    parameters(Symbol("offset").as(nonNegativeLong).?, Symbol("limit").as(nonNegativeLong).?) {
      (offset, limit) =>
        complete(groupMembership.listDevices(groupId, offset, limit))
    }

  def listGroups(ns: Namespace,
                 offset: Option[Long],
                 limit: Option[Long],
                 sortBy: GroupSortBy,
                 nameContains: Option[String]): Route =
    complete(db.run(GroupInfoRepository.list(ns, offset, limit, sortBy, nameContains)))

  def getGroup(groupId: GroupId): Route =
    complete(db.run(GroupInfoRepository.findByIdAction(groupId)))

  def createGroup(groupName: GroupName,
                  namespace: Namespace,
                  groupType: GroupType,
                  expression: Option[GroupExpression]): Route =
    complete(
      StatusCodes.Created -> groupMembership.create(groupName, namespace, groupType, expression)
    )

  def createGroupWithDevices(groupName: GroupName,
                             namespace: Namespace,
                             byteSource: Source[ByteString, Any])(
    implicit materializer: Materializer): Route = {

    val deviceIds = byteSource
      .via(Framing.delimiter(ByteString("\n"), DEVICE_OEM_ID_MAX_BYTES, allowTruncation = true))
      .map(_.utf8String)
      .map(DeviceOemId.apply)
      .runWith(Sink.seq)

    val deviceUuids = deviceIds
      .map(_.grouped(FILTER_EXISTING_DEVICES_BATCH_SIZE).toSeq)
      .map(_.map(_.toSet))
      .map(_.map(DeviceRepository.filterExisting(namespace, _)))
      .flatMap(dbActions => db.run(DBIO.sequence(dbActions)))
      .map(_.flatten)
      .recoverWith { case _: FramingException =>
        FastFuture.failed(Errors.MalformedInputFile)
      }

    val createGroupAndAddDevices =
      for {
        uuids <- deviceUuids
        gid <- groupMembership.create(groupName, namespace, GroupType.static, None)
        _ <- Future.traverse(uuids)(uuid => groupMembership.addGroupMember(gid, uuid))
      } yield gid

    complete(StatusCodes.Created -> createGroupAndAddDevices)
  }

  def deleteGroup(groupId: GroupId): Route = {
    val io = for {
      _ <- GroupMemberRepository.removeAllGroupMembers(groupId)
      _ <- GroupInfoRepository.deleteGroup(groupId)
    } yield StatusCodes.NoContent

    complete(db.run(io.transactionally))
  }

  private def renameGroup(groupId: GroupId, newGroupName: GroupName): Route =
    complete(db.run(GroupInfoRepository.renameGroup(groupId, newGroupName)))

  private def countDevices(groupId: GroupId): Route =
    complete(groupMembership.countDevices(groupId))

  private def findStats(groupId: GroupId): Route =
    complete(db.run(DeviceRepository.getDeviceGroupStats(groupId)))

  val countDevicesPerGroup: Route =
    parameter("groupIds".as(CsvSeq[GroupId])) { groupids =>
      val groupIdsSet = groupids.toSet

      if (groupids.length > 100)
        reject(ValidationRejection("too many groupIds to filter, maximum is 100"))
      else
        complete(groupMembership.countDevicesPerGroup(groupIdsSet).map { res =>
          Json.obj("values" -> res.asJson)
        })
    }

  def addDeviceToGroup(groupId: GroupId, deviceUuid: DeviceId): Route =
    complete(groupMembership.addGroupMember(groupId, deviceUuid))

  def removeDeviceFromGroup(groupId: GroupId, deviceId: DeviceId): Route =
    complete(groupMembership.removeGroupMember(groupId, deviceId))

  // This can take some time, req. timeout should be bigger and/or this should be done in the background
  def updateGroupHibernationStatus(ns: Namespace, groupId: GroupId): Route =
    post {
      entity(as[UpdateHibernationStatusRequest]) { req =>
        val f = db.run(GroupMemberRepository.setHibernationStatus(ns, groupId, req.status))
        complete(f.map(_ => StatusCodes.OK))
      }
    }

  val route: Route =
    (pathPrefix("device_groups") & namespaceExtractor) { ns =>
      pathEnd {
        (get & parameters(
          Symbol("offset").as(nonNegativeLong).?,
          Symbol("limit").as(nonNegativeLong).?,
          Symbol("sortBy").as[GroupSortBy].?,
          Symbol("nameContains").as[String].?
        )) { (offset, limit, sortBy, nameContains) =>
          listGroups(ns, offset, limit, sortBy.getOrElse(GroupSortBy.Name), nameContains)
        } ~
          post {
            entity(as[CreateGroup]) { req =>
              createGroup(req.name, ns, req.groupType, req.expression)
            } ~
              (fileUpload("deviceIds") & parameter(Symbol("groupName").as[GroupName])) {
                case ((_, byteSource), groupName) =>
                  createGroupWithDevices(groupName, ns, byteSource)
              }
          }
      } ~
        concat(
          (get & path("membership") & parameter("deviceUuids".as(CsvSeq[DeviceId]))) { deviceUuids =>
            complete(db.run(GroupMemberRepository.listGroupsForDevices(deviceUuids)))
          },
          (get & path("count")) {
            countDevicesPerGroup
          },
          GroupIdPath { groupId =>
            (get & pathEndOrSingleSlash) {
              getGroup(groupId)
            } ~
              path("hibernation") {
                updateGroupHibernationStatus(ns, groupId)
              } ~
              pathPrefix("devices") {
                get {
                  getDevicesInGroup(groupId)
                } ~
                  deviceNamespaceAuthorizer { deviceUuid =>
                    post {
                      addDeviceToGroup(groupId, deviceUuid)
                    } ~
                      delete {
                        removeDeviceFromGroup(groupId, deviceUuid)
                      }
                  }
              } ~
              delete {
                deleteGroup(groupId)
              } ~
              (put & path("rename") & parameter(Symbol("groupName").as[GroupName])) { groupName =>
                renameGroup(groupId, groupName)
              } ~
              (get & path("count") & pathEnd) {
                countDevices(groupId)
              } ~
              (get & path("device-stats")) {
                findStats(groupId)
              }
          }
        )
    }

}

case class CreateGroup(name: GroupName, groupType: GroupType, expression: Option[GroupExpression])

object CreateGroup {
  import io.circe.generic.semiauto.*

  implicit val createGroupEncoder: Encoder[CreateGroup] = deriveEncoder
  implicit val createGroupDecoder: Decoder[CreateGroup] = deriveDecoder
}
