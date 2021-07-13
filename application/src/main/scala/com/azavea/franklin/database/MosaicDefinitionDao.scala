package com.azavea.franklin.database

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
}
