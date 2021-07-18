package com.azavea.franklin.api.services

import cats.Parallel
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import com.azavea.franklin.api.endpoints._
import com.azavea.franklin.database.MosaicDefinitionDao
import com.azavea.franklin.database.StacCollectionDao
import com.azavea.franklin.database.StacItemDao
import com.azavea.franklin.datamodel.CollectionMosaicRequest
import com.azavea.franklin.datamodel.ItemAsset
import com.azavea.franklin.datamodel.{
  ItemRasterTileRequest,
  MapboxVectorTileFootprintRequest,
  TileJson
}
import com.azavea.franklin.error.{NotFound => NF}
import com.azavea.franklin.tile._
import doobie._
import doobie.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.layer.Implicits._
import geotrellis.raster.MosaicRasterSource
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
import java.util.UUID
import geotrellis.proj4.CRS
import geotrellis.layer.SpatialKey
import geotrellis.layer.ZoomedLayoutScheme
import geotrellis.proj4.WebMercator

class TileService[F[_]: Concurrent: Parallel: Logger: Timer: ContextShift](
    serverHost: NonEmptyString,
    enableTiles: Boolean,
    path: Option[String],
    xa: Transactor[F]
) extends Http4sDsl[F]
    with RenderImplicits {

  import CogAssetNodeImplicits._

  private val tmsLevels = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  private val invisiCellType = IntUserDefinedNoDataCellType(0)

  private val invisiTile: Tile = IntUserDefinedNoDataArrayTile(
    Array.fill(65536)(0),
    256,
    256,
    invisiCellType
  )

  private val invisiMBTile = MultibandTile(invisiTile, invisiTile, invisiTile)

  // todo
  // todo: memoize
  private def getHistogram(mosaicDefinitionId: UUID): F[Array[Histogram[Int]]] = ???

  // todo: memoize
  def getRasterSource(href: String): F[GeoTiffRasterSource] =
    Sync[F].delay(GeoTiffRasterSource(href))

  val tileEndpoints = new TileEndpoints(enableTiles, path)

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
      histograms <- EitherT.liftF(cogAssetNode.getHistograms[F])
      rs         <- EitherT.liftF(cogAssetNode.getRasterSource[F])
      tile <- EitherT {
        val eval = LayerTms.identity(cogAssetNode)
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
      collectionO <- StacCollectionDao.getCollection(decoded).transact(xa)
    } yield {
      Either.fromOption(
        collectionO map { collection =>
          TileJson.fromStacCollection(collection, serverHost).asJson
        },
        NF(s"Could not produce tile json for collection: $decoded")
      )
    }
  }

  // todo: memoize
  private def getItemsList(
      collectionId: String,
      mosaicDefinitionId: UUID,
      z: Int,
      x: Int,
      y: Int
  ): F[List[ItemAsset]] =
    (for {
      // todo: memoize
      mosaicDefinition <- MosaicDefinitionDao
        .getMosaicDefinition(
          collectionId,
          mosaicDefinitionId
        )
      itemsList <- mosaicDefinition traverse { mosaic =>
        MosaicDefinitionDao.getItems(mosaic.items, z, x, y)
      }
    } yield (itemsList getOrElse Nil)).transact(xa)

  def getCollectionMosaicTile(
      tileRequest: CollectionMosaicRequest
  ): F[Either[Unit, Array[Byte]]] = {
    val (z, x, y) = tileRequest.zxy

    for {
      itemAssets <- getItemsList(tileRequest.collection, tileRequest.mosaicId, z, x, y)
      _          <- Logger[F].debug(s"got items list with ${itemAssets.size} items")
      assetHrefs <- itemAssets.parTraverseN(8) {
        case ItemAsset(itemId, assetName) =>
          for {
            ()    <- Logger[F].debug(s"getting asset ${itemId}-${assetName}")
            asset <- StacItemDao.unsafeGetAsset(itemId, assetName).transact(xa)
            ()    <- Logger[F].debug(s"got asset ${itemId}-${assetName}")
          } yield asset
      }
      mosaicSource <- assetHrefs traverse { asset =>
        getRasterSource(asset.href)
      } map { sources =>
        sources.toNel map { rs => MosaicRasterSource(rs, CRS.fromEpsgCode(3857)) }
      }
      tileO <- mosaicSource flatTraverse { rs =>
        Sync[F].delay(
          rs.tileToLayout(tmsLevels(tileRequest.z)).read(SpatialKey(x, y), tileRequest.bands)
        )
      }
      // TODO memoize
      histO = tileO map { _.histogram }
    } yield {
      // TODO: nodata not getting set correctly
      val outTile = (tileO, histO) mapN {
        case (mbt, hists) =>
          MultibandTile(mbt.bands.zip(hists).map {
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
          })
      } getOrElse invisiMBTile
      Right(outTile.renderPng.bytes)
    }
  }

  val routes: HttpRoutes[F] =
    Http4sServerInterpreter.toRoutes(tileEndpoints.itemRasterTileEndpoint)(getItemRasterTile) <+>
      Http4sServerInterpreter.toRoutes(tileEndpoints.collectionFootprintTileEndpoint)(
        getCollectionFootprintTile
      ) <+> Http4sServerInterpreter.toRoutes(tileEndpoints.collectionFootprintTileJson)(
      getCollectionFootprintTileJson
    ) <+> Http4sServerInterpreter.toRoutes(tileEndpoints.collectionMosaicEndpoint)(
      getCollectionMosaicTile
    )

}
