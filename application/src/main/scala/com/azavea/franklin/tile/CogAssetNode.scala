package com.azavea.franklin.tile

import cats.data.{NonEmptyList => NEL}
import cats.effect.Sync
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.azavea.franklin.cache._
import com.azavea.stac4s.StacAsset
import geotrellis.layer._
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.resample._
import scalacache.modes.sync._
import scalacache.{Sync => _, _}

case class CogAssetNode(asset: StacAsset, bands: Seq[Int]) extends TileUtil {
  private val histoKey = s"histogram - $bands - ${asset.href}"
  private val tiffKey  = s"tiff - ${asset.href}"

  def getRasterSource[F[_]: Sync]: F[GeoTiffRasterSource] = {

    val infoFromCache = sync.get[SerializableGeotiffInfo](tiffKey)
    val tiffInfoF = infoFromCache match {
      case Some(info) => info.pure
      case _ =>
        for {
          info <- Sync[F].delay(GeotiffReader.getGeotiffInfo(asset.href))
          _ = sync.put(tiffKey)(info)
        } yield info
    }

    for {
      info <- tiffInfoF
      byteReader = getByteReader(asset.href)
      gtiffInfo  = info.toGeotiffInfo(byteReader)
      geotiff    = GeotiffReader.readMultibandWithInfo(gtiffInfo)
    } yield GeoTiffRasterSource(asset.href, baseTiff = Some(geotiff))
  }

  def getHistograms[F[_]: Sync]: F[List[Histogram[Int]]] = {
    val histogramFromSource = getRasterSource.map { rs =>
      val overviews = rs.tiff.overviews
      val smallestOverview = overviews.maxBy { overview =>
        val cs = overview.cellSize
        cs.width
      }
      bands.map { b => smallestOverview.tile.band(b).histogram }.toList
    }

    sync.get[List[Histogram[Int]]](histoKey) match {
      case Some(histograms) => histograms.pure
      case _ =>
        for {
          histograms <- histogramFromSource
          _          <- sync.put(histoKey)(histograms, None).pure
        } yield histograms
    }
  }

  def getRasterExtents[F[_]: Sync]: F[NEL[RasterExtent]] = {
    getRasterSource map { rs =>
      NEL
        .fromList(rs.resolutions.map { cs => RasterExtent(rs.extent, cs) })
        .getOrElse(NEL(rs.gridExtent.toRasterExtent, Nil))
    }
  }

  def fetchTile[F[_]: Sync](
      zoom: Int,
      x: Int,
      y: Int,
      crs: CRS = WebMercator,
      method: ResampleMethod = NearestNeighbor,
      target: ResampleTarget = DefaultTarget
  ): F[Raster[MultibandTile]] = {
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
