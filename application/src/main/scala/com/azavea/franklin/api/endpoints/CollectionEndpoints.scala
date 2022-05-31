package com.azavea.franklin.api.endpoints

import cats.effect.Concurrent
import com.azavea.franklin.api.schemas._
import com.azavea.franklin.datamodel.{CollectionsResponse, MosaicDefinition}
import com.azavea.franklin.datamodel.stactypes.{Collection}
import com.azavea.franklin.error.NotFound
import com.azavea.stac4s.StacCollection
import io.circe._
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.model.StatusCode.{NotFound => NF}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

import java.util.UUID

class CollectionEndpoints[F[_]: Concurrent](
    enableTransactions: Boolean,
    pathPrefix: Option[String]
) {

  val basePath = baseFor(pathPrefix, "collections")

  val base = endpoint.in(basePath)

  val collectionsList: Endpoint[Unit, Unit, CollectionsResponse, Fs2Streams[F]] =
    base.get
      .out(jsonBody[CollectionsResponse])
      .description("A list of collections")
      .name("collections")

  val createCollection: Endpoint[StacCollection, Unit, Json, Fs2Streams[F]] =
    base.post
      .in(accumulatingJsonBody[StacCollection])
      .out(jsonBody[Json])
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

  val collectionUnique: Endpoint[String, NotFound, Collection, Fs2Streams[F]] =
    base.get
      .in(path[String])
      .out(jsonBody[Collection])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("A single collection")
      .name("collectionUnique")

  val collectionTiles: Endpoint[String, NotFound, (Json, String), Fs2Streams[F]] =
    base.get
      .in(path[String] / "tiles")
      .out(jsonBody[Json])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .out(header[String]("ETag"))
      .description("A collection's tile endpoints")
      .name("collectionTiles")

  val createMosaic
      : Endpoint[(String, MosaicDefinition), NotFound, MosaicDefinition, Fs2Streams[F]] =
    base.post
      .in(path[String] / "mosaic")
      .in(accumulatingJsonBody[MosaicDefinition])
      .out(jsonBody[MosaicDefinition])
      .errorOut(
        oneOf(statusMapping(NF, jsonBody[NotFound].description("not found")))
      )
      .description("Create a mosaic from items in this collection")
      .name("collectionMosaicPost")

  val getMosaic: Endpoint[(String, UUID), NotFound, MosaicDefinition, Fs2Streams[F]] =
    base.get
      .in(path[String] / "mosaic" / path[UUID])
      .out(jsonBody[MosaicDefinition])
      .errorOut(
        oneOf(
          statusMapping(NF, jsonBody[NotFound].description("Mosaic does not exist in collection"))
        )
      )
      .description("Fetch a mosaic defined for this collection")
      .name("collectionMosaicGet")

  val deleteMosaic: Endpoint[(String, UUID), NotFound, Unit, Fs2Streams[F]] =
    base.delete
      .in(path[String] / "mosaic" / path[UUID])
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf(
          statusMapping(NF, jsonBody[NotFound].description("Mosaic does not exist in collection"))
        )
      )

  val listMosaics: Endpoint[String, NotFound, List[MosaicDefinition], Fs2Streams[F]] =
    base.get
      .in(path[String] / "mosaic")
      .out(jsonBody[List[MosaicDefinition]])
      .errorOut(
        oneOf(statusMapping(NF, jsonBody[NotFound].description("Collection does not exist")))
      )

  val endpoints = List(collectionsList, collectionUnique) ++ {
    if (enableTransactions) List(createCollection, deleteCollection) else Nil
  }
}
