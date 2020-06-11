package com.azavea.franklin.api.endpoints

import com.azavea.franklin.error.NotFound
import com.azavea.stac4s.StacCollection
import io.circe._
import sttp.model.StatusCode.{NotFound => NF}
import sttp.tapir._
import sttp.tapir.json.circe._

class CollectionEndpoints(enableTransactions: Boolean, enableTiles: Boolean) {

  val base = endpoint.in("collections")

  val collectionsList: Endpoint[Unit, Unit, Json, Nothing] =
    base.get
      .out(jsonBody[Json])
      .description("A list of collections")
      .name("collections")

  val createCollection: Endpoint[StacCollection, Unit, Json, Nothing] =
    base.post
      .in(jsonBody[StacCollection])
      .out(jsonBody[Json])
      .description("""
        | Create a new collection. Item links in the POSTed collection will be ignored in
        | the service of ensuring that we can turn around an HTTP response in a reasonable
        | quantity of time. To create items, POST them to this collection under /collections/<id>/items
        | """.trim.stripMargin)
      .name("postCollection")

  val collectionUnique: Endpoint[String, NotFound, Json, Nothing] =
    base.get
      .in(path[String])
      .out(jsonBody[Json])
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
  } ++ { if (enableTransactions) List(createCollection) else Nil }
}
