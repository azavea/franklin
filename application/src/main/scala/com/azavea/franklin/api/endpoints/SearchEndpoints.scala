package com.azavea.franklin.api.endpoints

import com.azavea.franklin.api.schemas._
import com.azavea.franklin.database._
import com.azavea.franklin.datamodel.{PaginationToken, SearchParameters}
import com.azavea.franklin.commands.ApiConfig
import com.azavea.stac4s.{Bbox, TemporalExtent}

import cats.effect._
import eu.timepit.refined.types.numeric.NonNegInt
import geotrellis.vector.Geometry
import geotrellis.vector.{io => _, _}
import org.http4s.Request
import io.circe.{Decoder, Encoder, Json}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir.codec.refined._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

class SearchEndpoints[F[_]: Concurrent](apiConfig: ApiConfig) {

  val base = endpoint.in(baseFor(apiConfig.path, "search"))

  implicit val searchParametersValidator: Validator[SearchParameters] =
    Validator.pass[SearchParameters]

  val nextToken: EndpointInput.Query[Option[PaginationToken]] =
    query[Option[PaginationToken]]("next")

  val searchParameters: EndpointInput[SearchParameters] =
    query[Option[Bbox]]("bbox")
      .and(query[Option[TemporalExtent]]("datetime"))
      .and(query[Option[Geometry]]("intersects"))
      .and(query[Option[List[String]]]("collections"))
      .and(query[Option[List[String]]]("ids"))
      .and(query[Option[NonNegInt]]("limit"))
      .and(query[Option[Json]]("query"))
      .and(query[Option[Json]]("filter"))
      .and(query[Option[String]]("filter_lang"))
      .and(query[Option[String]]("token"))
      .map(
        (tup: (
            Option[Bbox],
            Option[TemporalExtent],
            Option[Geometry],
            Option[List[String]],
            Option[List[String]],
            Option[NonNegInt],
            Option[Json],
            Option[Json],
            Option[String],
            Option[String]
        )) => {
          val (
            bbox,
            temporalExtent,
            intersects,
            collections,
            ids,
            limit,
            query,
            filter,
            filterLang,
            token
          ) = tup
          // query is empty here because entering query extension fields in url params is
          // completely insane
          SearchParameters(
            bbox,
            temporalExtent,
            intersects,
            collections getOrElse Nil,
            ids getOrElse Nil,
            limit,
            query,
            filter,
            filterLang,
            token
          )
        }
      )(sp =>
        (
          sp.bbox,
          sp.datetime,
          sp.intersects,
          Some(sp.collections),
          Some(sp.ids),
          sp.limit,
          sp.query,
          sp.filter,
          sp.filterLang,
          sp.token
        )
      )

  val searchPostInput: EndpointInput[SearchParameters] =
    jsonBody[SearchParameters]

  val searchGet: Endpoint[SearchParameters, Unit, Json, Fs2Streams[F]] =
    base
      .get
      .in(searchParameters)
      .out(jsonBody[Json])
      .description("Search endpoint for all collections")
      .name("search-get")

  val searchPost: Endpoint[SearchParameters, Unit, Json, Fs2Streams[F]] =
    base
      .post
      .in(searchPostInput)
      .out(jsonBody[Json])
      .description("Search endpoint using POST for all collections")
      .name("search-post")

  val endpoints = List(searchGet, searchPost)
}
