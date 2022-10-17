package com.azavea.franklin.datamodel

import com.azavea.stac4s.StacItem
import io.circe.{Decoder, Encoder, Json}

final case class ItemsResponse(
    features: List[StacItem],
    links: List[Link]
)

object ItemsResponse {

  implicit val encItemsResponse: Encoder[ItemsResponse] = Encoder.forProduct3(
    "type",
    "features",
    "links"
  )(resp => ("FeatureCollection", resp.features, resp.links))

  implicit val decItemsResponse: Decoder[ItemsResponse] = Decoder.forProduct3(
    "type",
    "features",
    "links"
  )((_: String, features: List[StacItem], links: List[Link]) => ItemsResponse(features, links))
}
