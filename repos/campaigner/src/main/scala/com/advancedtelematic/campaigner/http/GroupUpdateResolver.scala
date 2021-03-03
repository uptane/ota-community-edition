package com.advancedtelematic.campaigner.http

import akka.http.scaladsl.model.Uri
import com.advancedtelematic.campaigner.client.{DeviceRegistryClient, ResolverClient}
import com.advancedtelematic.campaigner.data.DataType.{GroupId, Update}
import com.advancedtelematic.campaigner.db.UpdateSupport
import com.advancedtelematic.libats.data.DataType.Namespace
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class GroupUpdateResolver(deviceRegistry: DeviceRegistryClient, resolver: ResolverClient, resolverUri: Uri)
                         (implicit db: Database, ec: ExecutionContext) extends UpdateSupport  {

  def groupUpdates(ns: Namespace, groups: Set[GroupId], patience: Duration = 10.seconds): Future[Seq[Update]] = {

    for {
      devices <- Future.sequence(groups.map(g =>
          deviceRegistry.allDevicesInGroup(ns, g, patience))).map(_.flatten)
      externalUpdates <- resolver.availableUpdatesFor(resolverUri, ns, devices)
      localUpdates <- updateRepo.findByExternalIds(ns, externalUpdates)
    } yield localUpdates

  }
}
