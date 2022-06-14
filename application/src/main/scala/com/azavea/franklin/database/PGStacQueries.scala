package com.azavea.franklin.database

import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.api.util._

import cats.data.OptionT
import cats.syntax.option._
import cats.syntax.foldable._
import cats.syntax.list._
import com.azavea.franklin.datamodel.{
  BulkExtent,
  Collection,
  SearchParameters,
  StacSearchCollection
}
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
import org.http4s.Method

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

  def getCollection(collectionId: String, apiConfig: ApiConfig): ConnectionIO[Option[Collection]] =
    fr"SELECT content FROM collections WHERE id = $collectionId"
      .query[Collection]
      .option
      .map({ maybeColl => maybeColl.map(UpdateCollectionLinks(apiConfig).apply) })

  def listCollections(apiConfig: ApiConfig): ConnectionIO[List[Collection]] =
    fr"SELECT content FROM collections"
      .query[Collection]
      .to[List]
      .map({ collList => collList.map(UpdateCollectionLinks(apiConfig).apply) })

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

  def getItem(collectionId: String, itemId: String, apiConfig: ApiConfig): ConnectionIO[Option[StacItem]] = {
    val params = SearchParameters.getItemById(collectionId, itemId)
    search(params, Method.GET, apiConfig)
      .map({ results => results.features.headOption })
  }

  def listItems(collectionId: String, limit: Int, token: Option[String], apiConfig: ApiConfig): ConnectionIO[StacSearchCollection] = {
    val params = SearchParameters.getItemsByCollection(collectionId, limit.some, token)
    search(params, Method.GET, apiConfig)
      .map(_.copy(context = None))
  }

  // Search
  def search(params: SearchParameters, method: Method, apiConfig: ApiConfig): ConnectionIO[StacSearchCollection] = {
    val req = params.asJson.deepDropNullValues
    fr"SELECT search($req::jsonb)"
      .query[StacSearchCollection]
      .unique
      .map({ res =>
        method match {
          case Method.GET  =>
            UpdateSearchLinks(params, apiConfig).GET(res)
          case Method.POST =>
            UpdateSearchLinks(params, apiConfig).POST(res)
          case _           =>
            throw new IllegalArgumentException("Only GET and POST methods are supported as arguments to search")
        }
      })
  }
}
