package com.advancedtelematic.libats.debug.db

import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import io.circe.Json
import slick.jdbc.MySQLProfile.api.*
import slick.jdbc.{GetResult, SQLActionBuilder, SetParameter}

import scala.concurrent.{ExecutionContext, Future}

object DbDebug {
  import com.advancedtelematic.libats.debug.DebugDatatype.*

  implicit val setDeviceId: slick.jdbc.SetParameter[DeviceId] = SetParameter[DeviceId] { (id, pp) =>
    pp.setString(id.uuid.toString)
  }

  implicit val setNamespace: slick.jdbc.SetParameter[Namespace] = SetParameter[Namespace] {
    (ns, pp) =>
      pp.setString(ns.get)
  }

  def query(title: String,
            query: SQLActionBuilder)(implicit db: Database, ec: ExecutionContext): Future[Table] = {
    implicit val getMap = GetResult[Map[String, Json]] { pr =>
      val rowAsMap = (1 to pr.numColumns).map { idx =>
        val columnName = pr.rs.getMetaData.getColumnName(idx)

        val columnVal = pr.nextObject() match {
          case v: String =>
            io.circe.parser.parse(v) match {
              case Left(_) =>
                Json.fromString(v)
              case Right(v) =>
                v
            }
          case v: java.lang.Boolean =>
            Json.fromBoolean(v)
          case v: java.sql.Timestamp =>
            Json.fromString(v.toInstant.toString)
          case v: java.lang.Long =>
            Json.fromLong(v)
          case v: java.lang.Short =>
            Json.fromInt(v.intValue())
          case v: java.lang.Integer =>
            Json.fromInt(v)
          case v: java.lang.Float =>
            Json.fromFloat(v).getOrElse(Json.fromInt(v.intValue()))
          case v if v == null =>
            Json.Null
          case v =>
            Json.fromString(s"$v ${v.getClass.getCanonicalName}")
        }

        columnName -> columnVal
      }.toMap

      rowAsMap
    }

    db.run(query.as[Map[String, Json]]).map { res =>
      val names = res.headOption.getOrElse(Map.empty).keys.toVector
      val values = res.map(_.values.toList)
      Table(title, names, values)
    }
  }

  def readTable[T](name: String, f: T => SQLActionBuilder)(
    q: T)(implicit db: Database, ec: ExecutionContext): Future[Table] =
    query(name, f(q))

  val DEFAULT_LIMIT = 200
}
