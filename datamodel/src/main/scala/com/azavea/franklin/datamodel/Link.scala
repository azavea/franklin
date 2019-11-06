package com.azavea.franklin.datamodel

import geotrellis.server.stac.{StacLinkType, StacMediaType}
import io.circe.Encoder
import io.circe.generic.semiauto._

case class Link(
    href: NonEmptyString,
    rel: StacLinkType,
    _type: Option[StacMediaType],
    title: Option[NonEmptyString]
)

object Link {

  implicit val encStacLink: Encoder[Link] = Encoder.forProduct4(
    "href",
    "rel",
    "type",
    "title"
  )(link => (link.href, link.rel, link._type, link.title))

  implicit val decoderLink = deriveDecoder[Link]
}
