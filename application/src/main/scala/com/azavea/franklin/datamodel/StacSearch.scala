package com.azavea.franklin.datamodel

import geotrellis.server.stac.StacItem
import io.circe._
import io.circe.syntax._

object StacSearch {

  implicit val stacSearchEncoder = new Encoder[StacSearch] {

    final def apply(a: StacSearch): Json = Json.obj(
      ("type", Json.fromString("FeatureCollection")),
      ("search:metadata", a.searchMetadata.asJson),
      ("features", a.features.asJson)
    )
  }

  implicit val stacSearchDecoder = new Decoder[StacSearch] {

    final def apply(c: HCursor): Decoder.Result[StacSearch] =
      for {
        metadata <- c.downField("search:metadata").as[SearchMetadata]
        features <- c.downField("features").as[List[StacItem]]
      } yield {
        new StacSearch(metadata, features)
      }
  }
}

case class StacSearch(
    searchMetadata: SearchMetadata,
    features: List[StacItem]
)
