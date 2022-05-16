package com.azavea.franklin.datamodel

import com.azavea.stac4s.{StacItem, StacLink}
import io.circe.{Decoder, Encoder, Json}

final case class CollectionItemsResponse(
    features: List[StacItem],
    links: List[StacLink]
)

object CollectionItemsResponse {

  implicit val encCollectionItemsResponse: Encoder[CollectionItemsResponse] = Encoder.forProduct3(
    "type",
    "features",
    "links"
  )(resp => ("FeatureCollection", resp.features, resp.links))

  implicit val decCollectionItemsResponse: Decoder[CollectionItemsResponse] = Decoder.forProduct3(
    "type",
    "features",
    "links"
  )((_: String, features: List[StacItem], links: List[StacLink]) =>
    CollectionItemsResponse(features, links)
  )
}

final case class CollectionItemsResponseJson(
    features: List[Json],
    links: List[Json]
)

object CollectionItemsResponseJson {

  implicit val encCollectionItemsResponse: Encoder[CollectionItemsResponseJson] = Encoder.forProduct3(
    "type",
    "features",
    "links"
  )(resp => ("FeatureCollection", resp.features, resp.links))

  implicit val decCollectionItemsResponse: Decoder[CollectionItemsResponseJson] = Decoder.forProduct3(
    "type",
    "features",
    "links"
  )((_: String, features: List[Json], links: List[Json]) =>
    CollectionItemsResponseJson(features, links)
  )
}
