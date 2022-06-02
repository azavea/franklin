package com.azavea.franklin.api.endpoints

import com.azavea.franklin.api.FranklinJsonPrinter._
import com.azavea.franklin.api.schemas._
import com.azavea.franklin.datamodel.{CollectionsResponse, MosaicDefinition}
import com.azavea.franklin.datamodel.stactypes.{Collection}
import com.azavea.franklin.error.NotFound

import cats.effect.Concurrent
import io.circe._
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.model.StatusCode.{NotFound => NF}
import sttp.tapir._
import sttp.tapir.generic.auto._

import java.util.UUID

class CollectionEndpoints[F[_]: Concurrent](
    enableTransactions: Boolean,
    pathPrefix: Option[String]
) {

  val basePath = baseFor(pathPrefix, "collections")

  val base = endpoint.in(basePath)

  val collectionUnique: Endpoint[String, NotFound, Collection, Fs2Streams[F]] =
    base.get
      .in(path[String])
      .out(jsonBody[Collection])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("A single collection")
      .name("collectionUnique")

  val collectionsList: Endpoint[Unit, Unit, CollectionsResponse, Fs2Streams[F]] =
    base.get
      .out(jsonBody[CollectionsResponse])
      .description("A list of collections")
      .name("collections")

  val postCollection: Endpoint[Collection, Unit, Collection, Fs2Streams[F]] =
    base.post
      .in(accumulatingJsonBody[Collection])
      .out(jsonBody[Collection])
      .description("""
        | Create a new collection. Item links in the POSTed collection will be ignored in
        | the service of ensuring that we can turn around an HTTP response in a reasonable
        | quantity of time. To create items, POST them to this collection under /collections/<id>/items
        | """.trim.stripMargin)
      .name("postCollection")

  val putCollection: Endpoint[Collection, Unit, Collection, Fs2Streams[F]] =
    base.put
      .in(accumulatingJsonBody[Collection])
      .out(jsonBody[Collection])
      .description("""
        | Create a new collection. Item links in the POSTed collection will be ignored in
        | the service of ensuring that we can turn around an HTTP response in a reasonable
        | quantity of time. To create items, POST them to this collection under /collections/<id>/items
        | """.trim.stripMargin)
      .name("putCollection")

  val deleteCollection: Endpoint[String, NotFound, Unit, Fs2Streams[F]] =
    base.delete
      .in(path[String])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .name("collectionDelete")

  val endpoints = List(collectionsList, collectionUnique) ++ {
    if (enableTransactions) List(postCollection, putCollection, deleteCollection) else Nil
  }
}
