package com.azavea.franklin.datamodel.stactypes

import com.azavea.stac4s.{StacCollection, StacLink}
import io.circe._
import io.circe.generic.semiauto._

case class Foo(a: Int, b: String, c: Boolean)

case class Asset(
    href: String,
    _type: Option[String],
    title: Option[String],
    description: Option[String],
    roles: Option[List[String]],
    media_type: Option[String]
)

object Asset {
  implicit val assetDecoder: Decoder[Asset] = deriveDecoder
  implicit val assetEncoder: Encoder[Asset] = deriveEncoder
}
