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
) {
  val minX = extent.spatial.bbox(0).xmin
  val minY = extent.spatial.bbox(0).ymin
}

object TileInfo {

  val webMercatorQuadLink = TileMatrixSetLink(
    "WebMercatorQuad",
    "http://schemas.opengis.net/tms/1.0/json/examples/WebMercatorQuad.json"
  )

  def fromStacItem(host: String, collectionId: String, item: StacItem): Option[TileInfo] = {
    val spatialExtent = SpatialExtent(List(item.bbox))
    val stacExtent    = StacExtent(spatialExtent, Interval(List.empty))

    // TODO: just `collect` this
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
          s"$host/tiles/collections/$collectionId/items/$encodedItemId/WebMercatorQuad/{z}/{x}/{y}/?asset=$encodedKey"
        val mediaType = Some(`image/png`)
        TileSetLink(href, StacLinkType.Item, mediaType, Some(key), Some(true))
    }
    cogTileLinks.isEmpty match {
      case false =>
        Some(TileInfo(stacExtent, None, None, List(webMercatorQuadLink), cogTileLinks.toList))
      case _ => None
    }
  }

  def fromStacCollection(host: String, collection: StacCollection): TileInfo = {
    val mvtHref =
      s"$host/tiles/collections/${collection.id}/footprint/{tileMatrixSetId}/{tileMatrix}/{tileCol}/{tileRow}"
    val tileEndpointLink = TileSetLink(
      mvtHref,
      StacLinkType.VendorLinkType("tiles"),
      Some(VendorMediaType("application/vnd.mapbox-vector-tile")),
      Some(s"${collection.id} -- Footprints"),
      Some(true)
    )

    val tileJsonHref =
      s"$host/tiles/collections/${collection.id}/footprint/tile-json"

    val tileJsonLink = TileSetLink(
      tileJsonHref,
      StacLinkType.VendorLinkType("tile-json"),
      Some(`application/json`),
      Some(s"${collection.id} -- Footprints TileJSON"),
      Some(false)
    )

    TileInfo(
      collection.extent,
      collection.title map { title => s"$title - MVT" },
      Some("Mapbox Vector Tile representation of item footprints for this collection"),
      List(webMercatorQuadLink),
      List(
        tileEndpointLink,
        tileJsonLink
      )
    )
  }
}
