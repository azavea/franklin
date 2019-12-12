package com.azavea.franklin.api.endpoints

import com.azavea.franklin.error.NotFound
import io.circe._
import tapir._
import tapir.json.circe._

object CollectionEndpoints {

  val base = endpoint.in("collections")

  val collectionsList: Endpoint[Unit, Unit, Json, Nothing] =
    base.get
      .out(jsonBody[Json])
      .description("A list of collections")
      .name("collections")

  val collectionUnique: Endpoint[String, NotFound, Json, Nothing] =
    base.get
      .in(path[String])
      .out(jsonBody[Json])
      .errorOut(oneOf(statusMapping(404, jsonBody[NotFound].description("not found"))))
      .description("A single collection")
      .name("collectionUnique")

  val endpoints = List(collectionsList, collectionUnique)
}
