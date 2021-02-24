package com.azavea.franklin.api.services

import cats.Parallel
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.endpoints._
import com.azavea.franklin.database.StacCollectionDao
import com.azavea.franklin.database.StacItemDao
import com.azavea.franklin.datamodel.{
  ItemRasterTileRequest,
  MapboxVectorTileFootprintRequest,
  TileJson
}
import com.azavea.franklin.error.{NotFound => NF}
import com.azavea.franklin.tile._
import doobie._
import doobie.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.raster.render.ColorRamps.greyscale
import geotrellis.raster.render.{Implicits => RenderImplicits}
import geotrellis.raster.{io => _, _}
import geotrellis.server.LayerTms
import geotrellis.server._
import io.chrisdavenport.log4cats.Logger
import io.circe.Json
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import sttp.tapir.server.http4s._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class TileService[F[_]: Concurrent](
    serverHost: NonEmptyString,
    enableTiles: Boolean,
    xa: Transactor[F]
)(
    implicit cs: ContextShift[F],
    timerF: Timer[F],
    logger: Logger[F],
    parallel: Parallel[F]
) extends Http4sDsl[F]
    with RenderImplicits {

  import CogAssetNodeImplicits._

  val tileEndpoints = new TileEndpoints(enableTiles)

  def getItemRasterTile(tileRequest: ItemRasterTileRequest): F[Either[NF, Array[Byte]]] = {
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
        tileRequest.singleBand map { sb =>
          List(sb.value)
        } getOrElse {
          tileRequest.bands
        }
      )
      histograms <- EitherT.liftF[F, NF, List[Histogram[Int]]](
        cogAssetNode.getHistograms
      )
      rs <- EitherT.liftF[F, NF, GeoTiffRasterSource] {
        cogAssetNode.getRasterSource
      }
      tile <- EitherT {
        val eval = LayerTms.concurrent(cogAssetNode)
        eval(z, x, y).map {
          case Valid(mbt: MultibandTile) if mbt.bandCount > 1 => {
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
          case Valid(mbt: MultibandTile) if mbt.bandCount == 1 =>
            val cmap = rs.tiff.options.colorMap getOrElse {
              val greyscaleRamp = greyscale(255)
              val hist          = histograms(0)
              val breaks        = hist.quantileBreaks(100)
              greyscaleRamp.toColorMap(breaks)
            }
            val renderedTile = cmap.render(mbt.band(0))
            Either.right(renderedTile.renderPng.bytes)
          case Invalid(e) => Either.left(NF(s"Could not produce tile: $e"))
        }
      }
    } yield tile
    tileEither.value
  }

  def getCollectionFootprintTile(
      tileRequest: MapboxVectorTileFootprintRequest
  ): F[Either[NF, Array[Byte]]] =
    for {
      mvt <- StacCollectionDao.getCollectionFootprintTile(tileRequest).transact(xa)
    } yield {
      Either.fromOption(
        mvt,
        NF(s"Could not produce tile for bounds: ${tileRequest.z}/${tileRequest.x}/${tileRequest.y}")
      )
    }

  def getCollectionFootprintTileJson(
      collectionId: String
  ): F[Either[NF, Json]] = {
    val decoded = URLDecoder.decode(collectionId, StandardCharsets.UTF_8.toString)
    for {
      collectionO <- StacCollectionDao.getCollectionUnique(decoded).transact(xa)
    } yield {
      Either.fromOption(
        collectionO map { collection =>
          TileJson.fromStacCollection(collection, serverHost).asJson
        },
        NF(s"Could not produce tile json for collection: $decoded")
      )
    }
  }

  val routes: HttpRoutes[F] =
    Http4sServerInterpreter.toRoutes(tileEndpoints.itemRasterTileEndpoint)(getItemRasterTile) <+>
      Http4sServerInterpreter.toRoutes(tileEndpoints.collectionFootprintTileEndpoint)(
        getCollectionFootprintTile
      ) <+> Http4sServerInterpreter.toRoutes(tileEndpoints.collectionFootprintTileJson)(
      getCollectionFootprintTileJson
    )

}
