package com.azavea.franklin.datamodel

import com.azavea.stac4s.{StacItem, StacLink}
import io.circe.{Decoder, Encoder, Json}

final case class ItemsResponse(
    features: List[StacItem],
    links: List[StacLink]
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
  )((_: String, features: List[StacItem], links: List[StacLink]) =>
    ItemsResponse(features, links)
  )
}

final case class ItemsResponseJson(
    features: List[Json],
    links: List[Json]
)

object ItemsResponseJson {

  implicit val encItemsResponse: Encoder[ItemsResponseJson] =
    Encoder.forProduct3(
      "type",
      "features",
      "links"
    )(resp => ("FeatureCollection", resp.features, resp.links))

  implicit val decItemsResponse: Decoder[ItemsResponseJson] =
    Decoder.forProduct3(
      "type",
      "features",
      "links"
    )((_: String, features: List[Json], links: List[Json]) =>
      ItemsResponseJson(features, links)
    )
}
