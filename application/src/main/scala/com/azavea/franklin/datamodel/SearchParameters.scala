package com.azavea.franklin.datamodel

import cats.syntax.all._
import com.azavea.franklin.api.schemas.bboxToString
import com.azavea.franklin.database.temporalExtentToString
import com.azavea.stac4s.{Bbox, TemporalExtent}
import eu.timepit.refined.types.numeric.NonNegInt
import geotrellis.vector.Geometry
import geotrellis.vector.io.json.GeometryFormats._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import io.circe.refined._
import io.circe.parser.{parse => parseJson}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
    token: Option[String]
) {

  def asQueryParameters: String = {
    val bboxQP = bbox map { box => s"bbox=${bboxToString(box)}" }
    val datetimeQP = datetime map { tempExtent =>
      s"datetime=${SearchParameters.encodeString(temporalExtentToString(tempExtent))}"
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
    val tokenQP = token map { t => s"""token=next:${SearchParameters.encodeString(t)}""" }

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
      tokenQP
    ).flatten.mkString("&")
  }

}

object SearchParameters {

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
        filterLang        <- c.downField("filter_lang").as[Option[String]] match {
          case Left(_)      => c.downField("filter-lang").as[Option[String]]
          case r @ Right(_) => r
        }
        token <- c.downField("token").as[Option[String]]
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
          token
        )
      }
  }

  implicit val searchFilterEncoder: Encoder[SearchParameters] = Encoder.forProduct10(
    "bbox",
    "datetime",
    "intersects",
    "collections",
    "ids",
    "limit",
    "query",
    "filter",
    "filterLang",
    "token"
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
      filters.token
    )
  )
}
