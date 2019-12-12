package com.azavea.franklin.database

import geotrellis.server.stac.{Bbox, TemporalExtent}
import geotrellis.vector.Geometry
import io.circe.generic.semiauto._
import geotrellis.server.stac.Implicits.{geometryDecoder, geometryEncoder}
import io.circe.{Decoder, HCursor}

final case class SearchFilters(
    bbox: Option[Bbox],
    datetime: Option[TemporalExtent],
    intersects: Option[Geometry],
    collections: List[String],
    items: List[String],
    limit: Option[Int],
    next: Option[String]
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
      } yield {
        SearchFilters(
          bbox,
          datetime,
          intersects,
          collectionsOption.getOrElse(List.empty),
          itemsOption.getOrElse(List.empty),
          limit,
          next
        )
      }
  }
  implicit val searchFilterEncoder = deriveEncoder[SearchFilters]
}
