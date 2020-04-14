package com.azavea.franklin.datamodel

import cats.implicits._
import com.azavea.stac4s._
import io.circe._
import io.circe.generic.JsonCodec

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@JsonCodec
case class TileMatrixSetLink(tileMatrixSet: String, tileMatrixSetURI: String)

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

@JsonCodec
case class TileInfo(
    extent: StacExtent,
    title: Option[String],
    description: Option[String],
    tilesetLinks: List[TileMatrixSetLink],
    links: List[TileSetLink]
)

object TileInfo {

  def fromStacItem(host: String, collectionId: String, item: StacItem): Option[TileInfo] = {
    val spatialExtent = SpatialExtent(List(item.bbox))
    val stacExtent    = StacExtent(spatialExtent, Interval(List.empty))
    val tileMatrixSetLink = TileMatrixSetLink(
      "WebMercatorQuad",
      "http://schemas.opengis.net/tms/1.0/json/examples/WebMercatorQuad.json"
    )
    val cogAssets = item.assets.filter {
      case (_, asset) =>
        asset._type match {
          case Some(`image/cog`) => true
          case _                 => false
        }
    }

    val cogTileLinks = cogAssets.map {
      case (key, _) =>
        val encodedItemId = URLEncoder.encode(item.id, StandardCharsets.UTF_8.toString)
        val encodedKey    = URLEncoder.encode(key, StandardCharsets.UTF_8.toString)
        val href =
          s"$host/tiles/collections/$collectionId/items/$encodedItemId/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}/?asset=$encodedKey"
        val mediaType = Some(`image/png`)
        TileSetLink(href, StacLinkType.Item, mediaType, None, Some(true))
    }
    cogTileLinks.isEmpty match {
      case false =>
        Some(TileInfo(stacExtent, None, None, List(tileMatrixSetLink), cogTileLinks.toList))
      case _ => None
    }
  }
}
