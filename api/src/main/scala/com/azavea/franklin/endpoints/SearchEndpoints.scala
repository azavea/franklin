package com.azavea.franklin.endpoints

import io.circe._
import tapir._
import tapir.json.circe._

object SearchEndpoints {

  val base = endpoint.in("stac")

  val rootCatalog: Endpoint[Unit, Unit, Json, Nothing] =
    base.get
      .out(jsonBody[Json])
      .description("Root Catalog and entrypoint into STAC collections")
      .name("root")

  val searchGet: Endpoint[Unit, Unit, Json, Nothing] =
    base.get
      .in("search")
      .out(jsonBody[Json])
      .description("Search endpoint for all collections")
      .name("search-get")

  val searchPost: Endpoint[Unit, Unit, Json, Nothing] =
    base.post
      .in("search")
      .out(jsonBody[Json])
      .description("Search endpoint using POST for all collections")
      .name("search-post")

  val endpoints = List(rootCatalog, searchGet, searchPost)

}
