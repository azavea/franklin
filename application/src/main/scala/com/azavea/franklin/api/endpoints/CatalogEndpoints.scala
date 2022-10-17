package com.azavea.franklin.api.endpoints

import cats.effect.Concurrent
import com.azavea.franklin.api.FranklinJsonPrinter._
import com.azavea.franklin.api.schemas._
import com.azavea.franklin.datamodel.{Catalog, CollectionsResponse}
import com.azavea.franklin.error.NotFound
import io.circe._
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.model.StatusCode.{NotFound => NF}
import sttp.tapir._
import sttp.tapir.generic.auto._

import java.util.UUID

class CatalogEndpoints[F[_]: Concurrent](
    pathPrefix: Option[String]
) {

  val basePath = baseFor(pathPrefix, "catalogs")

  val base = endpoint.in(basePath)

  val catalogUnique: Endpoint[List[String], NotFound, Catalog, Fs2Streams[F]] =
    base.get
      .in(paths)
      .out(jsonBody[Catalog])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("A single catalog")
      .name("catalogUnique")

  val endpoints = List(catalogUnique)
}
