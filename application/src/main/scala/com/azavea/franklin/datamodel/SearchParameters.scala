package com.azavea.franklin.datamodel

import cats.syntax.all._
import com.azavea.franklin.api.schemas.bboxToString
import com.azavea.stac4s.Bbox
import eu.timepit.refined.types.numeric.NonNegInt
import geotrellis.vector.Geometry
import geotrellis.vector.io.json.GeometryFormats._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.parser.{parse => parseJson}
import io.circe.refined._
import io.circe.syntax._

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

final case class Sorter(
    field: String,
    direction: String
) {

  def toQP =
    if (direction == "asc") s"+${field}"
    else s"-${field}"
}

object Sorter {

  def fromString(s: String): Sorter =
    if (s.startsWith(("-"))) {
      Sorter(s.drop(1), "desc")
    } else if (s.startsWith("+")) {
      Sorter(s.drop(1), "asc")
    } else {
      Sorter(s, "asc")
    }

  implicit val sorterDecoder: Decoder[Sorter] = deriveDecoder[Sorter]
  implicit val sorterEncoder: Encoder[Sorter] = deriveEncoder[Sorter]
}

// https://github.com/radiantearth/stac-api-spec/tree/master/item-search#query-parameter-table
final case class SearchParameters(
    bbox: Option[Bbox],
    datetime: Option[TemporalExtent],
    intersects: Option[Geometry],
    collections: List[String],
    ids: List[String],
    limit: Option[NonNegInt],
    query: Option[Json],
    filter: Option[Json],
    filterLang: Option[String],
    token: Option[String],
    sortby: Option[List[Sorter]]
) {

  def asQueryParameters: String = {
    val bboxQP = bbox map { box => s"bbox=${bboxToString(box)}" }
    val datetimeQP = datetime map { tempExtent =>
      s"datetime=${SearchParameters.encodeString(tempExtent.toString)}"
    }
    val intersectsQP = intersects map { intersectingGeom =>
      val geojsonString: String = intersectingGeom.asJson.noSpaces
      s"""intersects=${SearchParameters.encodeString(geojsonString)}"""
    }
    val collectionsQP = collections.toNel map { _ =>
      s"""collections=${SearchParameters.encodeString(collections.mkString(","))}"""
    }
    val idsQP = ids.toNel map { _ =>
      s"""ids=${SearchParameters.encodeString(ids.mkString(","))}"""
    }
    val limitQP  = limit map { lim => s"""limit=$lim""" }
    val queryQP  = query map { q => s"""query=${SearchParameters.encodeString(q.noSpaces)}""" }
    val filterQP = filter map { f => s"""filter=${SearchParameters.encodeString(f.noSpaces)}""" }
    val filterLangQP = filterLang map { fl =>
      s"""filter_lang=${SearchParameters.encodeString(fl)}"""
    }
    val tokenQP  = token map { t => s"""token=${t}""" }
    val sortbyQP = sortby map { t => s"sortby=${t.map(_.toQP).mkString(",")}" }

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

  def encodeString(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.toString)

  implicit val searchFilterDecoder = new Decoder[SearchParameters] {

    def apply(c: HCursor): Decoder.Result[SearchParameters] =
      for {
        bbox              <- c.downField("bbox").as[Option[Bbox]]
        datetime          <- c.downField("datetime").as[Option[TemporalExtent]]
        intersects        <- c.downField("intersects").as[Option[Geometry]]
        collectionsOption <- c.downField("collections").as[Option[List[String]]]
        idsOption         <- c.downField("ids").as[Option[List[String]]]
        limit             <- c.downField("limit").as[Option[NonNegInt]]
        query             <- c.downField("query").as[Option[Json]]
        filter            <- c.downField("filter").as[Option[Json]]
        filterLang <- c.downField("filter_lang").as[Option[String]] match {
          case Left(_)      => c.downField("filter-lang").as[Option[String]]
          case r @ Right(_) => r
        }
        token  <- c.downField("token").as[Option[String]]
        sortby <- c.downField("sortby").as[Option[List[Sorter]]]
      } yield {

        SearchParameters(
          bbox,
          datetime,
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
  )(filters =>
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
  )
}
