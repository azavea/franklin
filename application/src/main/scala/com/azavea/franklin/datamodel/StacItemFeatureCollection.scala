package com.azavea.franklin.datamodel

import geotrellis.server.stac.{StacItem, StacLink}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._

object StacItemFeatureCollection {

  implicit val stacItemFeatureCollectionEncoder: Encoder[StacItemFeatureCollection] =
    (fc: StacItemFeatureCollection) =>
      Json.obj(
        ("type", Json.fromString("FeatureCollection")),
        ("features", fc.features.asJson),
        ("links", fc.links.asJson)
    )

  implicit val stacItemFeatureCollectionDecoder: Decoder[StacItemFeatureCollection] =
    deriveDecoder[StacItemFeatureCollection]
}

final case class StacItemFeatureCollection(features: List[StacItem], links: List[StacLink])
