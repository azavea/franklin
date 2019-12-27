package com.azavea.franklin.api.endpoints

import com.azavea.franklin.error.NotFound
import io.circe._
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.model.StatusCode.{NotFound => NF}

object CollectionItemEndpoints {

  val base = endpoint.in("collections")

  val collectionItemsList: Endpoint[String, Unit, Json, Nothing] =
    base.get
      .in(path[String])
      .in("items")
      .out(jsonBody[Json])
      .description("A feature collection of collection items")
      .name("collectionItems")

  val collectionItemsUnique: Endpoint[(String, String), NotFound, Json, Nothing] =
    base.get
      .in(path[String] / "items" / path[String])
      .out(jsonBody[Json])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("A single feature")
      .name("collectionItemUnique")

  val endpoints = List(collectionItemsList, collectionItemsUnique)
}
