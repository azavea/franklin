package com.azavea.franklin.tile

import geotrellis.layer.{LayoutDefinition, ZoomedLayoutScheme}
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.vector.Extent

trait TileUtil {

  private val invisiTile: ByteArrayTile = ByteArrayTile.empty(256, 256)

  val invisiRaster: Raster[MultibandTile] = Raster(
    MultibandTile(invisiTile, invisiTile, invisiTile),
    Extent(0, 0, 256, 256)
  )

  val tmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray
}

object TileUtil extends TileUtil