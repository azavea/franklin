package com.azavea.franklin.database

import com.azavea.franklin.datamodel.Query
import com.azavea.stac4s.{Bbox, TemporalExtent}
import geotrellis.vector.Geometry
import geotrellis.vector.{io => _, _}
import io.circe.generic.semiauto._
import io.circe.{Decoder, HCursor}

final case class SearchFilters(
    bbox: Option[Bbox],
    datetime: Option[TemporalExtent],
    intersects: Option[Geometry],
    collections: List[String],
    items: List[String],
    limit: Option[Int],
    next: Option[String],
    query: Map[String, List[Query]]
) {
  val page = Page(limit, next)
}

object SearchFilters {

  implicit val searchFilterDecoder = new Decoder[SearchFilters] {

    final def apply(c: HCursor): Decoder.Result[SearchFilters] =
      for {
        bbox              <- c.downField("bbox").as[Option[Bbox]]
        datetime          <- c.downField("datetime").as[Option[TemporalExtent]]
        intersects        <- c.downField("intersects").as[Option[Geometry]]
        collectionsOption <- c.downField("collections").as[Option[List[String]]]
        itemsOption       <- c.downField("items").as[Option[List[String]]]
        limit             <- c.downField("limit").as[Option[Int]]
        next              <- c.downField("next").as[Option[String]]
        query             <- c.get[Map[String, List[Query]]]("query")
      } yield {
        SearchFilters(
          bbox,
          datetime,
          intersects,
          collectionsOption.getOrElse(List.empty),
          itemsOption.getOrElse(List.empty),
          limit,
          next,
          query
        )
      }
  }
  implicit val searchFilterEncoder = deriveEncoder[SearchFilters]
}
