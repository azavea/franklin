package com.azavea.franklin.api.endpoints

import com.azavea.franklin.error.NotFound
import io.circe._
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.model.StatusCode.{NotFound => NF}

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
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("A single collection")
      .name("collectionUnique")

  val endpoints = List(collectionsList, collectionUnique)
}
