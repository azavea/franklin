package com.azavea.franklin.database

import cats.data.NonEmptyList
import com.azavea.franklin.datamodel.ItemAsset
import com.azavea.franklin.datamodel.MosaicDefinition
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

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
}
