package com.azavea.franklin.database

import doobie._
import doobie.implicits._
import doobie.free.connection.ConnectionIO
import geotrellis.server.stac._

object StacCollectionDao {

  val selectF = fr"SELECT collection FROM collections"

  def listCollections(): ConnectionIO[List[StacCollection]] = {
    selectF.query[StacCollection].to[List]
  }

  def getCollectionUnique(
      collectionId: String
  ): ConnectionIO[Option[StacCollection]] = {
    (selectF ++ Fragments.whereAnd(
      fr"id = $collectionId"
    )).query[StacCollection].option
  }

  def insertStacCollection(collection: StacCollection): ConnectionIO[StacCollection] = {

    val insertFragment = fr"""
      INSERT INTO collections (id, collection) 
      VALUES
      (${collection.id}, $collection)
      """
    insertFragment.update
      .withUniqueGeneratedKeys[StacCollection]("collection")
  }
}
