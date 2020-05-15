package com.azavea.franklin.api.endpoints

import com.azavea.franklin.api.schemas._
import com.azavea.franklin.database._
import com.azavea.stac4s.{Bbox, TemporalExtent}
import io.circe._
import sttp.tapir._
import sttp.tapir.json.circe._

object SearchEndpoints {

  val base = endpoint.in("search")

  implicit val searchFiltersValidator: Validator[SearchFilters] = Validator.pass[SearchFilters]

  val searchFilters: EndpointInput[SearchFilters] =
    query[Option[TemporalExtent]]("datetime")
      .and(query[Option[Bbox]]("bbox"))
      .and(query[Option[List[String]]]("collections"))
      .and(query[Option[List[String]]]("ids"))
      .and(query[Option[Int]]("limit"))
      .and(query[Option[String]]("next"))
      .map(
        (tup: (
            Option[TemporalExtent],
            Option[Bbox],
            Option[List[String]],
            Option[List[String]],
            Option[Int],
            Option[String]
        )) => {
          val (temporalExtent, bbox, collections, ids, limit, next) = tup
          // query is empty here because entering query extension fields in url params is
          // completely insane
          SearchFilters(
            bbox,
            temporalExtent,
            None,
            collections getOrElse Nil,
            ids getOrElse Nil,
            limit,
            next,
            Map.empty
          )
        }
      )(sf => (sf.datetime, sf.bbox, Some(sf.collections), Some(sf.items), sf.limit, sf.next))

  val searchGet: Endpoint[SearchFilters, Unit, Json, Nothing] =
    base.get
      .in(searchFilters)
      .out(jsonBody[Json])
      .description("Search endpoint for all collections")
      .name("search-get")

  val searchPost: Endpoint[SearchFilters, Unit, Json, Nothing] =
    base.post
      .in(jsonBody[SearchFilters])
      .out(jsonBody[Json])
      .description("Search endpoint using POST for all collections")
      .name("search-post")

  val endpoints = List(searchGet, searchPost)
}
