package com.azavea.franklin.database

import geotrellis.server.stac.{Bbox, TemporalExtent}
import geotrellis.vector.Geometry
import io.circe.generic.semiauto._
import geotrellis.server.stac.Implicits.{geometryDecoder, geometryEncoder}

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
  implicit val searchFilterEncoder = deriveEncoder[SearchFilters]
  implicit val searchFilterDecoder = deriveDecoder[SearchFilters]
}
