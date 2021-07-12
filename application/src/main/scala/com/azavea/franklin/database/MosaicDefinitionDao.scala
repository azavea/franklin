package com.azavea.franklin.database

import com.azavea.franklin.datamodel.MosaicDefinition
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

object MosaicDefinitionDao extends Dao[MosaicDefinition] {
  val tableName = "mosaic_definitions"

  val selectF = fr"select mosaic from" ++ tableF

  def insert(
      mosaicDefinition: MosaicDefinition,
      collectionId: String
  ): ConnectionIO[MosaicDefinition] =
    fr"insert into mosaic_definitions (id, collection, mosaic) values (${mosaicDefinition.id}, $collectionId, $mosaicDefinition)".update
      .withUniqueGeneratedKeys[MosaicDefinition]("mosaic")
}
