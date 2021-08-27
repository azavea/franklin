package com.azavea.franklin.tile

import cats.data.OptionT
import cats.effect.Async
import cats.effect.Sync
import cats.syntax.all._
import com.azavea.franklin.database.StacItemDao
import com.azavea.stac4s.{`image/cog`, StacItem}
import doobie.Transactor
import doobie.implicits._
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.raster.histogram.Histogram
import scalacache.Cache
import scalacache.CatsEffect.modes.async
import scalacache.memoization.memoizeF

import scala.concurrent.duration._

object syntax {

  implicit class RasterOps(item: StacItem) {

    def getRasterSource[F[_]: Async](
        assetName: String
    )(implicit cache: Cache[GeoTiffRasterSource]): F[Option[GeoTiffRasterSource]] =
      item.assets.get(assetName).filter(_._type == Some(`image/cog`)) traverse { asset =>
        memoizeF(Some(60.seconds)) {
          Sync[F].delay(new GeoTiffRasterSource(asset.href))
        }
      }

    def getHistogram[F[_]: Async](
        assetName: String,
        xa: Transactor[F]
    )(
        implicit histCache: Cache[Option[Array[Histogram[Int]]]],
        rsCache: Cache[GeoTiffRasterSource]
    ): F[Option[Array[Histogram[Int]]]] =
      memoizeF(Some(60.seconds)) {
        (OptionT(StacItemDao.getHistogram(item.id, assetName).transact(xa)) orElseF {
          getRasterSource[F](assetName) flatMap {
            case Some(rs) =>
              val hists = rs.tiff.overviews
                .maxBy(_.cellSize.width)
                .tile
                .histogram
              StacItemDao.insertHistogram(item.id, assetName, hists).transact(xa) map { Some(_) }
            case None => Sync[F].pure(Option.empty[Array[Histogram[Int]]])
          }
        }).value
      }
  }
}
