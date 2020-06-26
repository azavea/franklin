package com.azavea.franklin.database

import com.azavea.franklin.datamodel.MapboxVectorTileFootprintRequest
import com.azavea.stac4s._
import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.refined.implicits._

object StacCollectionDao extends Dao[StacCollection] {

  val selectF = fr"SELECT collection FROM collections"

  val tableName = "collections"

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
          ) AS geom,
          item -> 'properties' ->> ${request.colorField} as color,
          item -> 'properties' as item_properties
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
