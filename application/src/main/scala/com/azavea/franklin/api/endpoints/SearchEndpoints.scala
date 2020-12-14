package com.azavea.franklin.api.endpoints

import com.azavea.franklin.api.schemas._
import com.azavea.franklin.database._
import com.azavea.franklin.datamodel.PaginationToken
import com.azavea.stac4s.Bbox
import com.azavea.stac4s.types.TemporalExtent
import eu.timepit.refined.types.numeric.NonNegInt
import io.circe._
import sttp.tapir._
import sttp.tapir.codec.refined._
import sttp.tapir.json.circe._

object SearchEndpoints {

  val base = endpoint.in("search")

  implicit val searchFiltersValidator: Validator[SearchFilters] = Validator.pass[SearchFilters]

  val nextToken: EndpointInput.Query[Option[PaginationToken]] =
    query[Option[PaginationToken]]("next")

  val searchFilters: EndpointInput[SearchFilters] =
    query[Option[TemporalExtent]]("datetime")
      .and(query[Option[Bbox]]("bbox"))
      .and(query[Option[List[String]]]("collections"))
      .and(query[Option[List[String]]]("ids"))
      .and(query[Option[NonNegInt]]("limit"))
      .and(nextToken)
      .map(
        (tup: (
            Option[TemporalExtent],
            Option[Bbox],
            Option[List[String]],
            Option[List[String]],
            Option[NonNegInt],
            Option[PaginationToken]
        )) => {
          val (temporalExtent, bbox, collections, ids, limit, token) = tup
          // query is empty here because entering query extension fields in url params is
          // completely insane
          SearchFilters(
            bbox,
            temporalExtent,
            None,
            collections getOrElse Nil,
            ids getOrElse Nil,
            limit,
            Map.empty,
            token
          )
        }
      )(sf => (sf.datetime, sf.bbox, Some(sf.collections), Some(sf.items), sf.limit, sf.next))

  val searchPostInput: EndpointInput[SearchFilters] =
    jsonBody[SearchFilters]
      .and(nextToken)
      .map[SearchFilters]({ (tup: (SearchFilters, Option[PaginationToken])) =>
        val (filters, token) = tup
        filters.copy(next = filters.next orElse token)
      })(filters => (filters, filters.next))

  val searchGet: Endpoint[SearchFilters, Unit, Json, Nothing] =
    base.get
      .in(searchFilters)
      .out(jsonBody[Json])
      .description("Search endpoint for all collections")
      .name("search-get")

  val searchPost: Endpoint[SearchFilters, Unit, Json, Nothing] =
    base.post
      .in(searchPostInput)
      .out(jsonBody[Json])
      .description("Search endpoint using POST for all collections")
      .name("search-post")

  val endpoints = List(searchGet, searchPost)
}
