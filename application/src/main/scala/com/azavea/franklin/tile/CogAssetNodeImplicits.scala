package com.azavea.franklin.tile

import cats.effect.ContextShift
import cats.effect.IO
import geotrellis.proj4.WebMercator
import geotrellis.raster._
import geotrellis.raster.io.geotiff.AutoHigherResolution
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.server.ExtentReification
import geotrellis.server.TmsReification
import geotrellis.vector.Extent

object CogAssetNodeImplicits extends TileUtil {

  implicit def cogAssetNodeTmsReification: TmsReification[CogAssetNode] =
    new TmsReification[CogAssetNode] {

      def tmsReification(
          self: CogAssetNode,
          buffer: Int
      )(implicit cs: ContextShift[IO]): (Int, Int, Int) => IO[ProjectedRaster[MultibandTile]] =
        (z: Int, x: Int, y: Int) => {
          def fetch(xCoord: Int, yCoord: Int): IO[Raster[MultibandTile]] = {
            self.fetchTile(z, xCoord, yCoord, WebMercator).flatMap(a => IO(a))
          }

          fetch(x, y).map { tile =>
            val extent = tmsLevels(z).mapTransform.keyToExtent(x, y)
            ProjectedRaster(tile.tile, extent, WebMercator)
          }
        }
    }

  implicit def cogAssetNodeExtentReification: ExtentReification[CogAssetNode] =
    new ExtentReification[CogAssetNode] {

      def extentReification(
          self: CogAssetNode
      )(implicit cs: ContextShift[IO]): (Extent, CellSize) => IO[ProjectedRaster[MultibandTile]] =
        (extent: Extent, cs: CellSize) => {
          self.getRasterSource map { rs =>
            rs.resample(
                TargetRegion(new GridExtent[Long](extent, cs)),
                NearestNeighbor,
                AutoHigherResolution
              )
              .read(extent)
              .map { ProjectedRaster(_, WebMercator) } match {
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
