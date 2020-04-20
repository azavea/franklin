package com.azavea.franklin

import com.azavea.franklin.tile.SerializableGeotiffInfo
import geotrellis.raster.histogram._
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import scalacache._
import scalacache.caffeine._

package object cache {

  implicit val histogramCache: Cache[List[Histogram[Int]]] = CaffeineCache[List[Histogram[Int]]]
  implicit val tiffCache: Cache[MultibandGeoTiff]          = CaffeineCache[MultibandGeoTiff]

  implicit val tiffInfoCache: Cache[SerializableGeotiffInfo] =
    CaffeineCache[SerializableGeotiffInfo]
}
