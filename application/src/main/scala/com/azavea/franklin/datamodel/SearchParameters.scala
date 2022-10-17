package com.azavea.franklin.datamodel

import cats.syntax.all._
import com.azavea.franklin.api.schemas.bboxToString
import com.azavea.franklin.error.ValidationError
import com.azavea.stac4s.meta.ForeignImplicits._
import com.azavea.stac4s.{Bbox, ThreeDimBbox, TwoDimBbox}
import geotrellis.vector.Geometry
import geotrellis.vector.io.json.GeometryFormats._
import io.circe._
import io.circe.parser.{parse => parseJson}
import io.circe.refined._
import io.circe.syntax._

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.{Instant, OffsetDateTime}

// https://github.com/radiantearth/stac-api-spec/tree/master/item-search#query-parameter-table
final case class SearchParameters(
    bbox: Option[Bbox],
    datetime: Option[String],
    intersects: Option[Geometry],
    collections: List[String],
    ids: List[String],
    limit: Option[Int],
    query: Option[Json],
    filter: Option[Json],
    filterLang: Option[String],
    token: Option[String],
    sortby: Option[List[SortDefinition]]
) {

  def asQueryParameters: String = {
    val bboxQP     = bbox map { box => s"bbox=${bboxToString(box)}" }
    val datetimeQP = datetime map { tempExtent => s"datetime=${tempExtent}" }
    val intersectsQP = intersects map { intersectingGeom =>
      val geojsonString: String = intersectingGeom.asJson.noSpaces
      s"intersects=${SearchParameters.encodeString(geojsonString)}"
    }
    val collectionsQP = collections.toNel map { _ =>
      s"collections=${SearchParameters.encodeString(collections.mkString(","))}"
    }
    val idsQP        = ids.toNel map { _ => s"ids=${SearchParameters.encodeString(ids.mkString(","))}" }
    val limitQP      = limit map { lim => s"limit=$lim" }
    val queryQP      = query map { q => s"query=${SearchParameters.encodeString(q.noSpaces)}" }
    val filterQP     = filter map { f => s"filter=${SearchParameters.encodeString(f.noSpaces)}" }
    val filterLangQP = filterLang map { fl => s"filter_lang=${SearchParameters.encodeString(fl)}" }
    val tokenQP      = token map { t => s"token=${t}" }
    val sortbyQP     = sortby map { s => s"sortby=${s.map(_.toQP).mkString(",")}" }

    List(
      bboxQP,
      datetimeQP,
      intersectsQP,
      collectionsQP,
      idsQP,
      limitQP,
      queryQP,
      filterQP,
      filterLangQP,
      tokenQP,
      sortbyQP
    ).flatten.mkString("&")
  }

}

object SearchParameters {

  def stringToRFC3339: String => Either[Throwable, Instant] =
    (s: String) => Either.catchNonFatal(OffsetDateTime.parse(s, RFC3339formatter).toInstant)

  def validate(params: SearchParameters): Option[ValidationError] = {
    val limitFailure = params.limit flatMap { limit =>
      if (limit < 1)
        Some(ValidationError("Limit must be at least 1"))
      else if (limit > 10000)
        Some(ValidationError("Limit must be no larger than 10000"))
      else
        None
    }
    val bboxAndIntersectionFailure = {
      if (params.bbox.isDefined && params.intersects.isDefined)
        Some(
          ValidationError(
            "Bounding box and intersection are mutually exclusive arguments; please provide one or the other but not both"
          )
        )
      else
        None
    }
    val bboxFailure = params.bbox.flatMap({
      case TwoDimBbox(xmin, ymin, xmax, ymax) =>
        if (xmax < xmin)
          Some(
            ValidationError(
              "Invalid bounding box: longitude maximum must not be smaller than longitude minimum"
            )
          )
        else if (ymax < ymin)
          Some(
            ValidationError(
              "Invalid bounding box: latitude maximum must not be smaller than latitude minimum"
            )
          )
        else
          None
      case ThreeDimBbox(xmin, ymin, zmin, xmax, ymax, zmax) =>
        if (xmax < xmin)
          Some(
            ValidationError(
              "Invalid bounding box: longitude maximum must not be smaller than longitude minimum"
            )
          )
        else if (ymax < ymin)
          Some(
            ValidationError(
              "Invalid bounding box: latitude maximum must not be smaller than latitude minimum"
            )
          )
        else if (zmax < zmin)
          Some(
            ValidationError(
              "Invalid bounding box: altitude maximum must not be smaller than altitude minimum"
            )
          )
        else
          None
    })
    val temporalFailure = params.datetime.flatMap({ dtStr =>
      if (dtStr
            .split("/")
            .map({
              case ".."  => true
              case other => stringToRFC3339(other).isRight
            })
            .forall(identity)) None
      else Some(ValidationError("Invalid datetime filter"))
    })

    limitFailure orElse bboxAndIntersectionFailure orElse bboxFailure orElse temporalFailure

  }

  def getItemById(collectionId: String, itemId: String): SearchParameters = SearchParameters(
    None,
    None,
    None,
    List(collectionId),
    List(itemId),
    None,
    None,
    None,
    None,
    None,
    None
  )

  def getItemsByCollection(
      collectionId: String,
      limit: Option[Int] = None,
      token: Option[String] = None
  ): SearchParameters = SearchParameters(
    None,
    None,
    None,
    List(collectionId),
    List.empty,
    limit,
    None,
    None,
    None,
    token,
    None
  )

  def encodeString(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.toString)

  implicit val searchFilterDecoder = new Decoder[SearchParameters] {

    def apply(c: HCursor): Decoder.Result[SearchParameters] =
      for {
        bbox              <- c.downField("bbox").as[Option[Bbox]]
        datetime          <- c.downField("datetime").as[Option[String]]
        intersects        <- c.downField("intersects").as[Option[Geometry]]
        collectionsOption <- c.downField("collections").as[Option[List[String]]]
        idsOption         <- c.downField("ids").as[Option[List[String]]]
        limit             <- c.downField("limit").as[Option[Int]]
        query             <- c.downField("query").as[Option[Json]]
        filter            <- c.downField("filter").as[Option[Json]]
        filterLang <- c.downField("filter_lang").as[Option[String]] match {
          case Left(_)      => c.downField("filter-lang").as[Option[String]]
          case r @ Right(_) => r
        }
        token  <- c.downField("token").as[Option[String]]
        sortby <- c.downField("sortby").as[Option[List[SortDefinition]]]
      } yield {
        val formattedDT = datetime.map({ str =>
          val parsed = str
            .split("/")
            .map({
              case ("" | "..") => ".."
              case other =>
                stringToRFC3339(other) match {
                  case Right(instant) => instant.toString
                  case Left(err)      => err.toString
                }
            })
          if (parsed.length > 1 && str.endsWith("/")) "bad datetime filter"
          else parsed.mkString("/")
        })
        SearchParameters(
          bbox,
          formattedDT,
          intersects,
          collectionsOption.getOrElse(List.empty),
          idsOption.getOrElse(List.empty),
          limit,
          query,
          filter,
          filterLang.map { _ => "cql2-json" },
          token,
          sortby
        )
      }
  }

  implicit val searchFilterEncoder: Encoder[SearchParameters] = Encoder.forProduct11(
    "bbox",
    "datetime",
    "intersects",
    "collections",
    "ids",
    "limit",
    "query",
    "filter",
    "filterLang",
    "token",
    "sortby"
  )({ filters =>
    (
      filters.bbox,
      filters.datetime,
      filters.intersects,
      if (filters.collections.length > 0) Some(filters.collections) else None,
      if (filters.ids.length > 0) Some(filters.ids) else None,
      filters.limit,
      filters.query,
      filters.filter,
      filters.filterLang,
      filters.token,
      filters.sortby
    )
  })
}
