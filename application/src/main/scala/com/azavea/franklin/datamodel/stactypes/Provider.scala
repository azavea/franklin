package com.azavea.franklin.datamodel.stactypes

import com.azavea.stac4s.{StacCollection, StacLink}
import io.circe._
import io.circe.generic.semiauto._


case class Provider(
  name: String,
  description: Option[String],
  roles: Option[List[String]],
  url: Option[String]
)

object Provider {
  implicit val providerDecoder: Decoder[Provider] = deriveDecoder
  implicit val providerEncoder: Encoder[Provider] = deriveEncoder
}