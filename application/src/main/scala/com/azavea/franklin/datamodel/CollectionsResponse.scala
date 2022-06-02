package com.azavea.franklin.datamodel

import com.azavea.franklin.datamodel.stactypes.{Collection}
import com.azavea.stac4s.{StacCollection, StacLink}
import io.circe._
import io.circe.generic.semiauto._

case class CollectionsResponse(
    collections: List[Collection],
    links: List[Link] = List()
)

object CollectionsResponse {

  implicit val collectionsResponseDecoder: Decoder[CollectionsResponse] =
    deriveDecoder[CollectionsResponse]

  implicit val collectionsResponseEncoder: Encoder[CollectionsResponse] =
    deriveEncoder[CollectionsResponse]
}
