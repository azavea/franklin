package com.azavea.franklin.database

import com.azavea.franklin.datamodel.{SearchMetadata, StacSearch}
import doobie._
import doobie.implicits._
import doobie.free.connection.ConnectionIO
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.server.stac.StacItem
import geotrellis.vector.Projected

object StacItemDao {

  val selectF = fr"SELECT item FROM collection_items"

  private def collectionFilter(collectionId: String): Fragment = {
    val jsString = s"""{"collection":"$collectionId"}"""
    fr"item @> $jsString :: jsonb"
  }

  def listCollectionItems(collectionId: String): ConnectionIO[List[StacItem]] = {
    (selectF ++ Fragments.whereAnd(
      collectionFilter(collectionId)
    )).query[StacItem]
      .to[List]
  }

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

  def getCollectionItemUnique(
      collectionId: String,
      itemId: String
  ): ConnectionIO[Option[StacItem]] = {
    (selectF ++ Fragments.whereAnd(
      fr"id = $itemId",
      collectionFilter(collectionId)
    )).query[StacItem].option
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
