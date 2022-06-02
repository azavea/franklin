package com.azavea.franklin

import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.raster.histogram._
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import scalacache._
import scalacache.caffeine._

package object cache {

  implicit val histogramCache: Cache[List[Histogram[Int]]] = CaffeineCache[List[Histogram[Int]]]
  implicit val tiffCache: Cache[MultibandGeoTiff]          = CaffeineCache[MultibandGeoTiff]

  implicit val histArrayCache: Cache[Option[Array[Histogram[Int]]]] =
    CaffeineCache[Option[Array[Histogram[Int]]]]

  implicit val gtRasterSourceCache: Cache[GeoTiffRasterSource] = CaffeineCache[GeoTiffRasterSource]

}
