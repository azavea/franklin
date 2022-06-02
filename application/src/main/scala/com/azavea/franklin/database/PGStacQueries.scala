package com.azavea.franklin.database

import cats.data.OptionT
import cats.syntax.foldable._
import cats.syntax.list._
import com.azavea.franklin.datamodel.StacSearchCollection
import com.azavea.franklin.datamodel.stactypes.Collection
import com.azavea.franklin.datamodel.{BulkExtent, SearchParameters}
import com.azavea.stac4s._
import com.azavea.stac4s.extensions.periodic.PeriodicExtent
import com.azavea.stac4s.syntax._
import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.postgres.circe.jsonb.implicits._
import doobie.refined.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.syntax._

import java.time.Instant

object PGStacQueries extends CirceJsonbMeta {

  // Collections

  def createCollection(collection: Collection): ConnectionIO[Unit] =
    sql"SELECT create_collection($collection::jsonb);"
      .query[Unit]
      .unique

  def updateCollection(collection: Collection): ConnectionIO[Unit] =
    sql"SELECT update_collection($collection::jsonb);"
      .query[Unit]
      .unique

  def deleteCollection(collectionId: String): ConnectionIO[Unit] =
    sql"SELECT delete_collection($collectionId::text);"
      .query[Unit]
      .unique

  def getCollection(collectionId: String): ConnectionIO[Option[Collection]] =
    fr"SELECT content FROM collections WHERE id = $collectionId"
      .query[Collection]
      .option

  def listCollections(): ConnectionIO[List[Collection]] =
    fr"SELECT content FROM collections"
      .query[Collection]
      .to[List]

  // Items

  def createItem(item: StacItem): ConnectionIO[Unit] =
    sql"SELECT create_item($item::jsonb);"
      .query[Unit]
      .unique

  def updateItem(item: StacItem): ConnectionIO[Unit] =
    sql"SELECT update_item($item::jsonb);"
      .query[Unit]
      .unique

  def deleteItem(collectionId: String, itemId: String): ConnectionIO[Unit] =
    sql"SELECT delete_item($collectionId, $itemId);"
      .query[Unit]
      .unique

  def getItem(collectionId: String, itemId: String): ConnectionIO[Option[StacItem]] = {
    val params = SearchParameters.getItemById(collectionId, itemId)
    search(params).map({ results => results.features.headOption })
  }

  def listItems(collectionId: String, limit: Int): ConnectionIO[List[StacItem]] =
    fr"SELECT content FROM items WHERE collection = $collectionId LIMIT $limit"
      .query[StacItem]
      .to[List]

  // Search

  def search(params: SearchParameters): ConnectionIO[StacSearchCollection] = {
    val req = params.asJson.deepDropNullValues
    fr"SELECT search($req::jsonb)"
      .query[StacSearchCollection]
      .unique
  }
}
