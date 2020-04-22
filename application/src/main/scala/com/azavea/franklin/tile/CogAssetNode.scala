package com.azavea.franklin.tile

import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import com.azavea.franklin.cache._
import com.azavea.stac4s.StacItemAsset
import geotrellis.layer._
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.resample._
import scalacache.modes.sync._
import scalacache.{Sync => _, _}

case class CogAssetNode(asset: StacItemAsset, bands: Seq[Int]) extends TileUtil {
  private val histoKey = s"histogram - $bands - ${asset.href}"
  private val tiffKey  = s"tiff - ${asset.href}"

  def getRasterSource: IO[GeoTiffRasterSource] = {

    val infoFromCache = sync.get[SerializableGeotiffInfo](tiffKey)
    val tiffInfoIO = infoFromCache match {
      case Some(info) => IO.pure(info)
      case _ => {
        for {
          info <- IO.delay(GeotiffReader.getGeotiffInfo(asset.href))
        } yield {
          sync.put(tiffKey)(info)
          info
        }
      }
    }

    for {
      info <- tiffInfoIO
      byteReader = getByteReader(asset.href)
      gtiffInfo  = info.toGeotiffInfo(byteReader)
      geotiff    = GeotiffReader.readMultibandWithInfo(gtiffInfo)
    } yield GeoTiffRasterSource(asset.href, baseTiff = Some(geotiff))
  }

  def getHistograms: IO[List[Histogram[Int]]] = {
    val histogramFromSource = getRasterSource.map { rs =>
      val overviews = rs.tiff.overviews
      val smallestOverview = overviews.maxBy { overview =>
        val cs = overview.cellSize
        cs.width
      }
      bands.map { b => smallestOverview.tile.band(b).histogram }.toList
    }

    sync.get[List[Histogram[Int]]](histoKey) match {
      case Some(histograms) => IO.pure(histograms)
      case _ => {
        for {
          histograms <- histogramFromSource
          _          <- IO.pure(sync.put(histoKey)(histograms, None))
        } yield histograms
      }
    }
  }

  def getRasterExtents: IO[NEL[RasterExtent]] = {
    getRasterSource map { rs =>
      NEL
        .fromList(rs.resolutions.map { cs => RasterExtent(rs.extent, cs) })
        .getOrElse(NEL(rs.gridExtent.toRasterExtent, Nil))
    }
  }

  def fetchTile(
      zoom: Int,
      x: Int,
      y: Int,
      crs: CRS = WebMercator,
      method: ResampleMethod = NearestNeighbor,
      target: ResampleTarget = DefaultTarget
  ): IO[Raster[MultibandTile]] = {
    getRasterSource map { rs =>
      val key              = SpatialKey(x, y)
      val layoutDefinition = tmsLevels(zoom)
      val rasterSource =
        rs.reproject(crs, target).tileToLayout(layoutDefinition, method)
      rasterSource.read(key, bands).map(Raster(_, layoutDefinition.mapTransform(key)))
    } map {
      case Some(t) =>
        t
      case _ =>
        invisiRaster
    }
  }

}
