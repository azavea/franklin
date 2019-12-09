package com.azavea.franklin.api.endpoints

import com.azavea.franklin.api.schemas._
import com.azavea.franklin.database._
import geotrellis.server.stac.{Bbox, TemporalExtent}
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

  val searchFilters: EndpointInput[SearchFilters] =
    query[Option[TemporalExtent]]("datetime")
      .and(query[Option[Bbox]]("bbox"))
      .and(query[Option[List[String]]]("collections"))
      .and(query[Option[List[String]]]("ids"))
      .and(query[Option[Int]]("limit"))
      .and(query[Option[String]]("next"))
      .map {
        case (temporalExtent, bbox, collectionsOption, idsOption, limit, next) =>
          SearchFilters(
            bbox,
            temporalExtent,
            None,
            collectionsOption.getOrElse(List.empty),
            idsOption.getOrElse(List.empty),
            limit,
            next
          )
      }(sf => (sf.datetime, sf.bbox, Some(sf.collections), Some(sf.items), sf.limit, sf.next))

  val searchGet: Endpoint[SearchFilters, Unit, Json, Nothing] =
    base.get
      .in("search")
      .in(searchFilters)
      .out(jsonBody[Json])
      .description("Search endpoint for all collections")
      .name("search-get")

  val searchPost: Endpoint[SearchFilters, Unit, Json, Nothing] =
    base.post
      .in("search")
      .in(jsonBody[SearchFilters])
      .out(jsonBody[Json])
      .description("Search endpoint using POST for all collections")
      .name("search-post")

  val endpoints = List(rootCatalog, searchGet, searchPost)
}
