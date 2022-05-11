package com.azavea.franklin.database

import io.circe._
import io.circe.syntax._
import cats.data.OptionT
import cats.syntax.foldable._
import cats.syntax.list._
import com.azavea.franklin.datamodel.{BulkExtent, MapboxVectorTileFootprintRequest}
import com.azavea.stac4s._
import com.azavea.stac4s.extensions.periodic.PeriodicExtent
import com.azavea.stac4s.syntax._
import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.refined.implicits._
import doobie.postgres.circe.jsonb.implicits._
import eu.timepit.refined.types.string.NonEmptyString

import java.time.Instant

object StacCollectionDao extends Dao[StacCollection] {

  val selectF = fr"SELECT content FROM collections"

  val tableName = "collections"

  def listCollections(): ConnectionIO[List[Json]] =
    selectF.query[Json].to[List]

  def updateExtent(
      collectionId: String,
      bulkExtent: BulkExtent
  ): ConnectionIO[Int] =
    (OptionT(getCollection(collectionId)) flatMap { collection =>
      val existingExtent = collection.extent
      val newBbox: List[Bbox] = existingExtent.spatial.bbox match {
        case h :: t =>
          h.union(bulkExtent.bbox) :: t
        case Nil => List(bulkExtent.bbox)
      }

      val newTemporal = existingExtent.temporal.interval match {
        case TemporalExtent(s, e) :: _ =>
          val newStart: Option[Instant] = bulkExtent.start map { start =>
            s map { priorStart =>
              if (start.isBefore(priorStart)) { start }
              else priorStart
            }
          } getOrElse s

          val newEnd: Option[Instant] = bulkExtent.end map { end =>
            e map { priorEnd =>
              if (priorEnd.isBefore(end)) { end }
              else priorEnd
            }
          } getOrElse e

          List(TemporalExtent(newStart, newEnd)) ++ existingExtent.temporal.interval.tail
        case _ =>
          List(TemporalExtent(bulkExtent.start, bulkExtent.end))
      }

      val nonPeriodicInterval = Interval(newTemporal)
      val periodic            = existingExtent.temporal.getExtensionFields[PeriodicExtent]

      val newExtent = periodic.fold(
        _ => StacExtent(SpatialExtent(newBbox), nonPeriodicInterval),
        periodicInfo => {
          val periodicInterval = nonPeriodicInterval.addExtensionFields(periodicInfo)
          StacExtent(SpatialExtent(newBbox), periodicInterval)
        }
      )

      val newCollection: StacCollection = collection.copy(extent = newExtent)

      OptionT.liftF {
        fr"update collections set collection = ${newCollection} where id = ${collectionId};".update.run
      }
    }).getOrElse(0)

  def getCollectionCount(): ConnectionIO[Int] = {
    sql"select count(*) from collections".query[Int].unique
  }

  def getCollection(
      collectionId: String
  ): ConnectionIO[Option[StacCollection]] =
    query.filter(fr"id = $collectionId").selectOption

  def getCollectionJson(
      collectionId: String
  ): ConnectionIO[Option[Json]] =
    genericQuery[Json].filter(fr"id = $collectionId").selectOption

  def insertStacCollection(
      collection: StacCollection,
      parentId: Option[String]
  ): ConnectionIO[StacCollection] = {

    OptionT { getCollection(collection.id) } getOrElseF {
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
