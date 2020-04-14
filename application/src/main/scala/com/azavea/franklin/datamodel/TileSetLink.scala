package com.azavea.franklin.datamodel

import cats.implicits._
import com.azavea.stac4s._
import io.circe._

case class TileSetLink(
    href: String,
    rel: StacLinkType,
    _type: Option[StacMediaType],
    title: Option[String],
    templated: Option[Boolean]
)

object TileSetLink {

  implicit val encTileSetLink: Encoder[TileSetLink] = Encoder.forProduct5(
    "href",
    "rel",
    "type",
    "title",
    "templated"
  )(link => (link.href, link.rel, link._type, link.title, link.templated))

  implicit val decStacLink: Decoder[TileSetLink] = new Decoder[TileSetLink] {

    def apply(c: HCursor) =
      (
        c.downField("href").as[String],
        c.downField("rel").as[StacLinkType],
        c.get[Option[StacMediaType]]("type"),
        c.get[Option[String]]("title"),
        c.get[Option[Boolean]]("templated")
      ).mapN(
        (
            href: String,
            rel: StacLinkType,
            _type: Option[StacMediaType],
            title: Option[String],
            templated: Option[Boolean]
        ) => TileSetLink(href, rel, _type, title, templated)
      )
  }
}
