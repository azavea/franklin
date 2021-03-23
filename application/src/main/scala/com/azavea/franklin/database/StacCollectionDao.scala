package com.azavea.franklin.database

import cats.data.OptionT
import cats.syntax.foldable._
import cats.syntax.list._
import com.azavea.franklin.datamodel.{BulkExtent, MapboxVectorTileFootprintRequest}
import com.azavea.stac4s._
import com.azavea.stac4s.types.TemporalExtent
import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.refined.implicits._
import eu.timepit.refined.types.string.NonEmptyString

import java.time.Instant

object StacCollectionDao extends Dao[StacCollection] {

  val selectF = fr"SELECT collection FROM collections"

  val tableName = "collections"

  def listCollections(): ConnectionIO[List[StacCollection]] = {
    selectF.query[StacCollection].to[List]
  }

  def updateExtent(
      collectionId: String,
      bulkExtent: BulkExtent
  ): ConnectionIO[Int] =
    (OptionT(getCollection(collectionId)) flatMap { collection =>
      val existingExtent = collection.extent
      val newBbox = existingExtent.spatial.bbox match {
        case h :: t =>
          (bulkExtent.bbox map { h.union(_) } getOrElse h) :: t
        case Nil => bulkExtent.bbox.toList
      }

      val newTemporal = existingExtent.temporal.interval.map(_.value) match {
        case (s :: e :: Nil) :: _ =>
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

          val newTemporalExtent: List[TemporalExtent] = TemporalExtent
            .from(List(newStart, newEnd))
            .fold({ _ =>
              List.empty[TemporalExtent]
            }, List(_)) ++ existingExtent.temporal.interval.tail

          newTemporalExtent
        case _ =>
          TemporalExtent
            .from(List(bulkExtent.start, bulkExtent.end))
            .fold(
              _ => Nil,
              List(_)
            )
      }

      val newCollection: StacCollection = collection.copy(
        extent = StacExtent(
          SpatialExtent(newBbox),
          Interval(newTemporal)
        )
      )

      OptionT.liftF {
        fr"update collections set collection = ${newCollection} where id = ${collectionId};".update.run
      }
    }).getOrElse(0)

  def getCollectionCount(): ConnectionIO[Int] = {
    sql"select count(*) from collections".query[Int].unique
  }

  def getCollection(
      collectionId: String
  ): ConnectionIO[Option[StacCollection]] = query.filter(fr"id = $collectionId").selectOption

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
