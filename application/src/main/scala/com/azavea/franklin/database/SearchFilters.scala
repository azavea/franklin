package com.azavea.franklin.database

import cats.implicits._
import com.azavea.franklin.datamodel.{PaginationToken, Query}
import com.azavea.franklin.api.schemas.bboxToString
import com.azavea.stac4s.{Bbox, TemporalExtent}
import eu.timepit.refined.types.numeric.NonNegInt
import geotrellis.vector.Geometry
import geotrellis.vector.{io => _, _}
import io.circe.generic.semiauto._
import io.circe.refined._
import io.circe.{Decoder, HCursor}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

final case class SearchFilters(
    bbox: Option[Bbox],
    datetime: Option[TemporalExtent],
    intersects: Option[Geometry],
    collections: List[String],
    items: List[String],
    limit: Option[NonNegInt],
    query: Map[String, List[Query]],
    next: Option[PaginationToken]
) {

  def asQueryParameters: String = {

    val bboxQP =
      bbox map { box => s"bbox=${bboxToString(box)}" }
    val datetimeQP =
      datetime map { tempExtent =>
        s"datetime=${SearchFilters.encodeString(temporalExtentToString(tempExtent))}"
      }
    val collectionsQP = collections.toNel map { _ =>
      s"""collections=${SearchFilters.encodeString(collections.mkString(","))}"""
    }
    val itemsQP = items.toNel map { _ =>
      s"""ids=${SearchFilters.encodeString(items.mkString(","))}"""
    }

    List(bboxQP, datetimeQP, collectionsQP, itemsQP).flatten.mkString("&")
  }

}

object SearchFilters {

  def encodeString(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.toString)

  implicit val searchFilterDecoder = new Decoder[SearchFilters] {

    final def apply(c: HCursor): Decoder.Result[SearchFilters] =
      for {
        bbox              <- c.downField("bbox").as[Option[Bbox]]
        datetime          <- c.downField("datetime").as[Option[TemporalExtent]]
        intersects        <- c.downField("intersects").as[Option[Geometry]]
        collectionsOption <- c.downField("collections").as[Option[List[String]]]
        itemsOption       <- c.downField("items").as[Option[List[String]]]
        limit             <- c.downField("limit").as[Option[NonNegInt]]
        query             <- c.get[Option[Map[String, List[Query]]]]("query")
        paginationToken   <- c.get[Option[PaginationToken]]("next")
      } yield {
        SearchFilters(
          bbox,
          datetime,
          intersects,
          collectionsOption.getOrElse(List.empty),
          itemsOption.getOrElse(List.empty),
          limit,
          query getOrElse Map.empty,
          paginationToken
        )
      }
  }
  implicit val searchFilterEncoder = deriveEncoder[SearchFilters]
}
