package com.azavea.franklin.tile

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.raster.io.geotiff.AutoHigherResolution
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.server.ExtentReification
import geotrellis.server.TmsReification
import geotrellis.vector.Extent

object CogAssetNodeImplicits extends TileUtil {

  implicit def cogAssetNodeTmsReification[F[_]: Sync]: TmsReification[F, CogAssetNode] = {
    (self, _) => (z: Int, x: Int, y: Int) =>
      {
        def fetch(xCoord: Int, yCoord: Int): F[Raster[MultibandTile]] = {
          self.fetchTile(z, xCoord, yCoord, WebMercator).flatMap(a => Sync[F].delay(a))
        }

        fetch(x, y).map { tile =>
          val extent = tmsLevels(z).mapTransform.keyToExtent(x, y)
          ProjectedRaster(tile.tile, extent, WebMercator)
        }
      }
  }

  implicit def cogAssetNodeExtentReification[F[_]: Sync]: ExtentReification[F, CogAssetNode] = {
    self => (extent: Extent, ocs: Option[CellSize]) =>
      {
        self.getRasterSource map { rs =>
          val cs = ocs.getOrElse(rs.cellSize)
          rs.resample(
              TargetRegion(new GridExtent[Long](extent, cs)),
              NearestNeighbor,
              AutoHigherResolution
            )
            .read(extent)
            .map(ProjectedRaster(_, WebMercator)) match {
            case Some(mbt) => mbt
            case _ =>
              throw new Exception(
                s"No tile available for RasterExtent: ${RasterExtent(extent, cs)}"
              )
          }
        }
      }
  }
}
