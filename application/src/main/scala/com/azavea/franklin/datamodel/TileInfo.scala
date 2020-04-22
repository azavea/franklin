package com.azavea.franklin.datamodel

import com.azavea.stac4s._
import io.circe.generic.JsonCodec

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@JsonCodec
case class TileInfo(
    extent: StacExtent,
    title: Option[String],
    description: Option[String],
    tileMatrixSetLinks: List[TileMatrixSetLink],
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
          s"$host/tiles/collections/$collectionId/items/$encodedItemId/{tileMatrixSetId}/{tileMatrix}/{tileCol}/{tileRow}/?asset=$encodedKey"
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
