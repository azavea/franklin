package com.azavea.franklin.database

import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import com.azavea.stac4s._

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

  def insertStacCollection(
      collection: StacCollection,
      parentId: Option[String]
  ): ConnectionIO[StacCollection] = {

    val insertFragment = fr"""
      INSERT INTO collections (id, parent, collection)
      VALUES
      (${collection.id}, $parentId, $collection)
      """
    insertFragment.update
      .withUniqueGeneratedKeys[StacCollection]("collection")
  }
}
