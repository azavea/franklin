package com.azavea.franklin.api.endpoints

import cats.syntax.either._
import cats.effect._
import com.azavea.franklin.api.FranklinJsonPrinter._
import com.azavea.franklin.api.schemas._
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.database._
import com.azavea.franklin.error.ValidationError
import com.azavea.franklin.datamodel.{
  SearchParameters,
  SortDefinition,
  StacSearchCollection,
  TemporalExtent
}
import com.azavea.stac4s.Bbox
import com.azavea.stac4s.meta.ForeignImplicits._
import geotrellis.vector.Geometry
import geotrellis.vector.{io => _, _}
import io.circe.{Decoder, Encoder, Json}
import org.http4s.Request
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{Header, MediaType}
import sttp.tapir._
import sttp.tapir.codec.refined._
import sttp.tapir.generic.auto._
import sttp.model.StatusCode.BadRequest

import java.time.{Instant, OffsetDateTime}


class SearchEndpoints[F[_]: Concurrent](apiConfig: ApiConfig) {

  val base = endpoint.in(baseFor(apiConfig.path, "search"))

  implicit val searchParametersValidator: Validator[SearchParameters] =
    Validator.pass[SearchParameters]

  val searchParameters: EndpointInput[SearchParameters] =
    query[Option[Bbox]]("bbox")
      .and(query[Option[String]]("datetime"))
      .and(query[Option[Geometry]]("intersects"))
      .and(query[Option[List[String]]]("collections"))
      .and(query[Option[List[String]]]("ids"))
      .and(query[Option[Int]]("limit"))
      .and(query[Option[Json]]("query"))
      .and(query[Option[Json]]("filter"))
      .and(query[Option[String]]("filter_lang"))
      .and(query[Option[String]]("token"))
      .and(query[Option[List[String]]]("sortby"))
      .map(
        (tup: (
            Option[Bbox],
            Option[String],
            Option[Geometry],
            Option[List[String]],
            Option[List[String]],
            Option[Int],
            Option[Json],
            Option[Json],
            Option[String],
            Option[String],
            Option[List[String]]
        )) => {
          val (
            bbox,
            datetime,
            intersects,
            collections,
            ids,
            limit,
            query,
            filter,
            filterLang,
            token,
            sortby
          ) = tup

          def stringToRFC3339: String => Either[Throwable, Instant] =
            (s: String) => Either.catchNonFatal(OffsetDateTime.parse(s, RFC3339formatter).toInstant)
          val formattedDT = datetime.map({ str =>
            val parsed = str.split("/").map({
              case ("" | "..") => ".."
              case other =>
                stringToRFC3339(other) match {
                  case Right(res) => res.toString
                  case Left(err) => err.toString
                }
            })
            if (parsed.length > 1 && str.endsWith("/") ) "bad datetime filter"
            else parsed.mkString("/")
          })
          // query is empty here because entering query extension fields in url params is
          // completely insane
          SearchParameters(
            bbox,
            formattedDT,
            intersects,
            collections getOrElse Nil,
            ids getOrElse Nil,
            limit,
            query,
            filter,
            filterLang,
            token,
            sortby.map(_.map(SortDefinition.fromQP).toList)
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
          sp.token,
          sp.sortby.map(lst => lst.map(_.toQP))
        )
      )

  val searchPostInput: EndpointInput[SearchParameters] =
    jsonBody[SearchParameters]

  val searchGet: Endpoint[SearchParameters, ValidationError, StacSearchCollection, Fs2Streams[F]] =
    base.get
      .in(searchParameters)
      .out(jsonBody[StacSearchCollection])
      .out(header(Header.contentType(MediaType("application", "geo+json"))))
      .errorOut(
        oneOf(
          statusMapping(
            BadRequest,
            jsonBody[ValidationError]
              .description("Something was wrong with the body of the request")
          )
        )
      )
      .description("Search endpoint for all collections")
      .name("search-get")

  val searchPost: Endpoint[SearchParameters, ValidationError, StacSearchCollection, Fs2Streams[F]] =
    base.post
      .in(searchPostInput)
      .out(jsonBody[StacSearchCollection])
      .out(header(Header.contentType(MediaType("application", "geo+json"))))
      .errorOut(
        oneOf(
          statusMapping(
            BadRequest,
            jsonBody[ValidationError]
              .description("Something was wrong with the body of the request")
          )
        )
      )
      .description("Search endpoint using POST for all collections")
      .name("search-post")

  val endpoints = List(searchGet, searchPost)
}
