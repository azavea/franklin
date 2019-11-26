package com.azavea.franklin.database

import com.azavea.franklin.datamodel.{SearchMetadata, StacSearch}
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.server.stac._
import geotrellis.vector.Projected

object StacItemDao extends Dao[StacItem] {

  val tableName = "collection_items"

  val selectF = fr"SELECT item FROM " ++ tableF

  def getSearchResult(limit: Int, offset: Int): ConnectionIO[StacSearch] = {
    for {
      items   <- (selectF ++ fr"LIMIT $limit OFFSET $offset").query[StacItem].to[List]
      matched <- fr"SELECT count(1) FROM collection_items".query[Int].unique
    } yield {
      val next =
        if ((limit + offset) < matched) Some(NonEmptyString.unsafeFrom(s"${limit + offset}"))
        else None
      val metadata = SearchMetadata(next, items.length, limit, matched)
      StacSearch(metadata, items)
    }

  }

  def insertStacItem(item: StacItem): ConnectionIO[StacItem] = {

    val projectedGeometry = Projected(item.geometry, 4326)
    val itemExtent        = Projected(projectedGeometry.envelope.toPolygon, 4326)

    val insertFragment = fr"""
      INSERT INTO collection_items (id, extent, geom, item) 
      VALUES
      (${item.id}, $itemExtent, $projectedGeometry, $item)
      """
    insertFragment.update
      .withUniqueGeneratedKeys[StacItem]("item")
  }

}
