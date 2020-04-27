package com.azavea.franklin.api.endpoints

import com.azavea.franklin.error.NotFound
import io.circe._
import sttp.model.StatusCode.{NotFound => NF}
import sttp.tapir._
import sttp.tapir.json.circe._
import com.azavea.franklin.api._

class CollectionEndpoints(enableTiles: Boolean) {

  val base = endpoint.in("collections")

  val collectionsList: Endpoint[AcceptHeader, Unit, JsonOrHtmlOutput, Nothing] =
    base.get
      .in(acceptHeaderInput)
      .out(header[String]("content-type"))
      .out(jsonBody[Option[Json]])
      .out(plainBody[Option[String]])
      .description("A list of collections")
      .name("collections")

  val collectionUnique: Endpoint[(AcceptHeader, String), NotFound, JsonOrHtmlOutput, Nothing] =
    base.get
      .in(acceptHeaderInput)
      .in(path[String])
      .out(header[String]("content-type"))
      .out(jsonBody[Option[Json]])
      .out(plainBody[Option[String]])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("A single collection")
      .name("collectionUnique")

  val collectionTiles: Endpoint[String, NotFound, (Json, String), Nothing] =
    base.get
      .in(path[String] / "tiles")
      .out(jsonBody[Json])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .out(header[String]("ETag"))
      .description("A collection's tile endpoints")
      .name("collectionTiles")

  val endpoints = List(collectionsList, collectionUnique) ++ {
    if (enableTiles) List(collectionTiles) else Nil
  }
}
