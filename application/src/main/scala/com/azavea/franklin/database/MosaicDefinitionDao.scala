package com.azavea.franklin.database

import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.LiftIO
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.functor._
import cats.syntax.traverse._
import com.azavea.franklin.datamodel.ItemAsset
import com.azavea.franklin.datamodel.MosaicDefinition
import com.azavea.stac4s.StacAsset
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import geotrellis.raster.geotiff.GeoTiffRasterSource
import geotrellis.raster.histogram.Histogram

import java.util.UUID

object MosaicDefinitionDao extends Dao[MosaicDefinition] {
  val tableName = "mosaic_definitions"

  val selectF = fr"select mosaic from" ++ tableF

  def insert(
      mosaicDefinition: MosaicDefinition,
      collectionId: String
  ): ConnectionIO[MosaicDefinition] =
    fr"insert into mosaic_definitions (id, collection, mosaic) values (${mosaicDefinition.id}, $collectionId, $mosaicDefinition)".update
      .withUniqueGeneratedKeys[MosaicDefinition]("mosaic")

  private def collectionMosaicQB(collectionId: String, mosaicDefinitionId: UUID) =
    query.filter(mosaicDefinitionId).filter(fr"collection = $collectionId")

  def listMosaicDefinitions(
      collectionId: String
  ): ConnectionIO[List[MosaicDefinition]] =
    query.filter(fr"collection_id = $collectionId").list

  def getMosaicDefinition(
      collectionId: String,
      mosaicDefinitionId: UUID
  ): ConnectionIO[Option[MosaicDefinition]] =
    collectionMosaicQB(collectionId, mosaicDefinitionId).selectOption

  def deleteMosaicDefinition(collectionId: String, mosaicDefinitionId: UUID): ConnectionIO[Int] =
    collectionMosaicQB(collectionId, mosaicDefinitionId).delete

  def getItems(
      itemAssets: NonEmptyList[ItemAsset],
      z: Int,
      x: Int,
      y: Int
  ): ConnectionIO[List[ItemAsset]] = {
    val iaToString  = (ia: ItemAsset) => s""""${ia.itemId}""""
    val itemStrings = itemAssets.toList map iaToString
    val itemStringArray =
      s"""{ ${itemStrings.mkString(", ")} }"""
    fr"""
    with item_ids as (
      select unnest($itemStringArray :: text[]) as item_id
    )
    select id from item_ids join collection_items on item_ids.item_id = collection_items.id
    where st_intersects(collection_items.geom, st_transform(ST_TileEnvelope(${z},${x},${y}), 4326))
    """.query[String].to[List] map { itemIds =>
      val itemIdsSet = itemIds.toSet
      itemAssets.filter(ia => itemIdsSet.contains(ia.itemId))
    }
  }

  def insertHistogram(
      mosaicDefinitionId: UUID,
      assets: NonEmptyList[(String, String, StacAsset)]
  ): ConnectionIO[Unit] =
    assets traverse {
      case (itemId, assetName, asset) =>
        val fallbackHistIO: IO[Array[Histogram[Int]]] = IO.delay(
          GeoTiffRasterSource(asset.href).tiff.overviews
            .maxBy(_.cellSize.width)
            .tile
            .histogram
        )
        val histFromDb: OptionT[ConnectionIO, Array[Histogram[Int]]] =
          OptionT(StacItemDao.getHistogram(itemId, assetName))
        histFromDb getOrElseF
          (LiftIO[ConnectionIO].liftIO(
            fallbackHistIO
          ) flatMap { hists => StacItemDao.insertHistogram(itemId, assetName, hists) })
    } flatMap { hists =>
      // add all the histograms to the individual items
      // also update the mosaic definition with the merged histogram
      val merged =
        hists.tail.foldLeft(hists.head)((h1, h2) =>
          h1.zip(h2)
            .map({
              case (hist1, hist2) => hist1.merge(hist2)
            })
        )
      fr"""update mosaic_definitions set histograms = ${merged} where id = $mosaicDefinitionId""".update.run.void
    }

  def getHistogramUnsafe(mosaicDefinitionId: UUID): ConnectionIO[Option[Array[Histogram[Int]]]] =
    fr"select histograms from mosaic_definitions where id = $mosaicDefinitionId"
      .query[Array[Histogram[Int]]]
      .option
}
