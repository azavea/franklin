package com.azavea.franklin.api.endpoints

import cats.effect.Concurrent
import com.azavea.franklin.datamodel.TileInfo
import com.azavea.franklin.error.NotFound
import com.azavea.stac4s.StacCollection
import io.circe._
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode.{NotFound => NF}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

class CollectionEndpoints[F[_]: Concurrent](enableTransactions: Boolean, enableTiles: Boolean) {

  val base = endpoint.in("collections")

  val collectionsList: Endpoint[Unit, Unit, Json, Fs2Streams[F]] =
    base.get
      .out(jsonBody[Json])
      .description("A list of collections")
      .name("collections")

  val createCollection: Endpoint[StacCollection, Unit, StacCollection, Fs2Streams[F]] =
    base.post
      .in(jsonBody[StacCollection])
      .out(jsonBody[StacCollection])
      .description("""
        | Create a new collection. Item links in the POSTed collection will be ignored in
        | the service of ensuring that we can turn around an HTTP response in a reasonable
        | quantity of time. To create items, POST them to this collection under /collections/<id>/items
        | """.trim.stripMargin)
      .name("postCollection")

  val deleteCollection: Endpoint[String, NotFound, Unit, Fs2Streams[F]] =
    base.get
      .in(path[String])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .name("collectionDelete")

  val collectionUnique: Endpoint[String, NotFound, StacCollection, Fs2Streams[F]] =
    base.get
      .in(path[String])
      .out(jsonBody[StacCollection])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("A single collection")
      .name("collectionUnique")

  val collectionTiles: Endpoint[String, NotFound, (TileInfo, String), Fs2Streams[F]] =
    base.get
      .in(path[String] / "tiles")
      .out(jsonBody[TileInfo])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .out(header[String]("ETag"))
      .description("A collection's tile endpoints")
      .name("collectionTiles")

  val endpoints = List(collectionsList, collectionUnique) ++ {
    if (enableTiles) List(collectionTiles) else Nil
  } ++ { if (enableTransactions) List(createCollection, deleteCollection) else Nil }
}
