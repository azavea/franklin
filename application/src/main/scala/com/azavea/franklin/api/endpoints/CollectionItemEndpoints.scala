package com.azavea.franklin.api.endpoints

import com.azavea.franklin.api.schemas._
import com.azavea.franklin.error.{NotFound, ValidationError}
import io.circe._
import sttp.tapir._
import sttp.model.StatusCode.{NotFound => NF, BadRequest}
import com.azavea.stac4s.StacItem

class CollectionItemEndpoints(enableTransactions: Boolean) {

  println(enableTransactions)

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

  val postItem: Endpoint[(String, StacItem), ValidationError, Json, Nothing] =
    base.post
      .in(path[String] / "items")
      .in(jsonBody[StacItem])
      .out(jsonBody[Json])
      .errorOut(
        oneOf(
          statusMapping(
            BadRequest,
            jsonBody[ValidationError]
              .description("Collection in route did not match collection in item")
          )
        )
      )
      .description("Create a new feature in a collection")
      .name("postItem")

  val endpoints = List(collectionItemsList, collectionItemsUnique)
}
