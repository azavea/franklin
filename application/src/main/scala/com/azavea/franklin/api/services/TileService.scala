package com.azavea.franklin.api.services

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.effect._
import cats.implicits._
import com.azavea.franklin.api.endpoints._
import com.azavea.franklin.database.StacItemDao
import com.azavea.franklin.error.{NotFound => NF}
import com.azavea.franklin.tile._
import doobie._
import doobie.implicits._
import geotrellis.raster._
import geotrellis.server.LayerTms
import geotrellis.server._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

class TileService[F[_]: Sync: LiftIO](enableTiles: Boolean, xa: Transactor[F])(
    implicit cs: ContextShift[F],
    csIO: ContextShift[IO]
) extends Http4sDsl[F] {

  import CogAssetNodeImplicits._

  val tileEndpoints = new TileEndpoints(enableTiles)

  def getTile(tileRequest: RasterTileRequest): F[Either[NF, Array[Byte]]] = {
    val assetKey     = tileRequest.asset
    val collectionId = tileRequest.collection
    val itemId       = tileRequest.item
    val (z, x, y)    = tileRequest.zxy

    val tileEither = for {
      item <- EitherT.fromOptionF(
        StacItemDao.getCollectionItem(collectionId, itemId).transact(xa),
        NF(s"Could not find item ($itemId) in collection ($collectionId)")
      )
      cogAsset <- EitherT.fromOption[F](
        item.assets.get(assetKey),
        NF(s"Could not find asset ($assetKey) in item ($itemId)")
      )
      cogAssetNode = CogAssetNode(
        cogAsset,
        tileRequest.bands
      )
      histograms <- EitherT.liftF[F, NF, List[Histogram[Int]]](
        LiftIO[F].liftIO(cogAssetNode.getHistograms)
      )
      tile <- EitherT {
        val eval = LayerTms.identity(cogAssetNode)
        LiftIO[F].liftIO(eval(z, x, y).map {
          case Valid(mbt: MultibandTile) => {
            Either.right {
              val bands = mbt.bands.zip(histograms).map {
                case (tile, histogram) =>
                  val breaks = histogram.quantileBreaks(100)
                  val oldMin = breaks(tileRequest.lowerQuantile)
                  val oldMax = breaks(tileRequest.upperQuantile)
                  tile
                    .mapIfSet { cell =>
                      if (cell < oldMin) oldMin
                      else if (cell > oldMax) oldMax
                      else cell
                    }
                    .normalize(oldMin, oldMax, 1, 255)
              }
              MultibandTile(bands)
                .renderPng()
                .bytes
            }
          }
          case Invalid(e) => Either.left(NF(s"Could not produce tile: $e"))
        })
      }
    } yield tile
    tileEither.value
  }

  val routes: HttpRoutes[F] = tileEndpoints.tileEndpoint.get.toRoutes {
    case (tileRequest) => {
      getTile(tileRequest)
    }
  }
}
