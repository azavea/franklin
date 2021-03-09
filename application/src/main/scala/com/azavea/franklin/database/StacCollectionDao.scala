package com.azavea.franklin.database

import cats.data.OptionT
import cats.syntax.foldable._
import cats.syntax.list._
import com.azavea.franklin.datamodel.{BulkExtent, MapboxVectorTileFootprintRequest}
import com.azavea.stac4s._
import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.refined.implicits._
import eu.timepit.refined.types.string.NonEmptyString

object StacCollectionDao extends Dao[StacCollection] {

  val selectF = fr"SELECT collection FROM collections"

  val tableName = "collections"

  def listCollections(): ConnectionIO[List[StacCollection]] = {
    selectF.query[StacCollection].to[List]
  }

  def updateExtent(
      collectionId: String,
      bulkExtent: BulkExtent
  ): ConnectionIO[Option[StacCollection]] = ???

  def getCollectionCount(): ConnectionIO[Int] = {
    sql"select count(*) from collections".query[Int].unique
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

    OptionT { getCollectionUnique(collection.id) } getOrElseF {
      val insertFragment = fr"""
      INSERT INTO collections (id, parent, collection)
      VALUES
      (${collection.id}, $parentId, $collection)
      """
      insertFragment.update
        .withUniqueGeneratedKeys[StacCollection]("collection")

    }

  }

  private def sanitizeField(s: NonEmptyString): NonEmptyString =
    NonEmptyString.unsafeFrom(s.value.replace(":", "_"))

  private def fieldSelectF(request: MapboxVectorTileFootprintRequest): Fragment =
    (request.withField map { field =>
      Fragment.const(s", item -> 'properties' ->> '$field' as ${sanitizeField(field)}")
    }).intercalate(fr"")

  def getCollectionFootprintTile(
      request: MapboxVectorTileFootprintRequest
  ): ConnectionIO[Option[Array[Byte]]] = {
    val fragment = fr"""
    WITH mvtgeom AS
      (
        SELECT
          ST_AsMVTGeom(
            ST_Transform(geom, 3857),
            ST_TileEnvelope(${request.z},${request.x},${request.y})
          ) AS geom
          """ ++ fieldSelectF(request) ++ fr"""
        FROM collection_items
        WHERE
          ST_Intersects(
            geom,
            ST_Transform(
              ST_TileEnvelope(${request.z},${request.x},${request.y}),
              4326
            )
          ) AND
          item ->> 'collection' = ${request.collection}
      )
    SELECT ST_AsMVT(mvtgeom.*) FROM mvtgeom;
    """
    fragment
      .query[Array[Byte]]
      .option
  }
}
