/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.http

import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.byteArrayUnmarshaller
import akka.pattern.ask
import com.advancedtelematic.treehub.db.ObjectRepositorySupport
import com.advancedtelematic.util.FakeUsageUpdate.{CurrentBandwith, CurrentStorage}
import com.advancedtelematic.util.ResourceSpec.ClientTObject
import com.advancedtelematic.util.{LongHttpRequest, LongTest, ResourceSpec, TreeHubSpec}

import java.util.UUID
import scala.concurrent.duration.*

class ObjectResourceSpec extends TreeHubSpec with ResourceSpec with LongHttpRequest with LongTest with ObjectRepositorySupport {

  test("POST creates a new blob when uploading form with `file` field") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Array[Byte]] shouldBe obj.blob
    }
  }

  // This is the same as using `curl -H "Content-Type: application/octet-stream" --data-binary @file`
  test("POST creates a new blob when uploading application/octet-stream directly") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.blob) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Get(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Array[Byte]] shouldBe obj.blob
    }
  }

  test("DELETE removes object") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.blob) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Delete(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Get(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("DELETE removes object from database and underlying storage") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.blob) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Delete(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    objectRepository.find(defaultNs, obj.objectId).failed.futureValue.getMessage shouldBe Errors.ObjectNotFound(obj.objectId).getMessage
    objectStore.exists(defaultNs, obj.objectId).futureValue shouldBe false
    localFsBlobStore.exists(defaultNs, obj.objectId.asPrefixedPath).futureValue shouldBe false
  }

  test("DELETE keeps other objects intact") {
    val obj = new ClientTObject()
    val obj2 = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.blob) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Post(apiUri(s"objects/${obj2.prefixedObjectId}"), obj.blob) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Delete(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Get(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }

    Get(apiUri(s"objects/${obj2.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("DELETE returns 404 when object does not exist") {
    val obj = new ClientTObject()

    Delete(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("POST fails if object is empty and server does not support out of band upload") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }


  test("cannot report object upload when object is not in status CLIENT_UPLOADING") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.blob) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Put(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("request is still accepted and valid when client supports out of band storage but server does not") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}?size=${obj.blob.length}"), obj.blob)
      .addHeader(new OutOfBandStorageHeader) ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }

    Get(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Array[Byte]] shouldBe obj.blob
    }
  }

  test("POST hints updater to update current storage") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val usage = fakeUsageUpdate.ask(CurrentStorage(defaultNs))(1.second).mapTo[Long].futureValue

    usage should be >= 1L
  }

  test("GET hints updater to update current bandwidth") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Get(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    val usage = fakeUsageUpdate.ask(CurrentBandwith(obj.objectId))(1.second).mapTo[Long].futureValue

    usage should be >= obj.blob.length.toLong
  }

  test("409 for already existing objects") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.Conflict
    }
  }

  test("404 for non existing objects") {
    val obj = new ClientTObject()

    Get(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("matches only valid commits") {
    Get(apiUri(s"objects/wat/w00t")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("HEAD returns 404 if commit does not exist") {
    val obj = new ClientTObject()

    Head(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("HEAD returns 200 if commit exists") {
    val obj = new ClientTObject()

    Post(apiUri(s"objects/${obj.prefixedObjectId}"), obj.form) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }

    Head(apiUri(s"objects/${obj.prefixedObjectId}")) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("returns 404 if commit doesn't exist, with a uuid as namespace") {
    val obj = new ClientTObject()

    Get(apiUri(s"objects/${obj.prefixedObjectId}")).addHeader(RawHeader("x-ats-namespace", UUID.randomUUID().toString)) ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }
}
