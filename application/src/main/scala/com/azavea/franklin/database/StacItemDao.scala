package com.azavea.franklin.database

import com.azavea.franklin.datamodel.{SearchMetadata, StacSearchCollection}
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import com.azavea.stac4s._
import geotrellis.vector.Projected

object StacItemDao extends Dao[StacItem] {

  val tableName = "collection_items"

  val selectF = fr"SELECT item FROM " ++ tableF

  def getSearchResult(searchFilters: SearchFilters): ConnectionIO[StacSearchCollection] = {
    val page = searchFilters.page
    for {
      items   <- query.filter(searchFilters).list(searchFilters.page)
      matched <- query.filter(searchFilters).count
    } yield {
      val next =
        if ((items.length + page.offset) < matched)
          page.nextPage.next.flatMap(s => NonEmptyString.from(s).toOption)
        else None
      val metadata = SearchMetadata(next, items.length, page.limit, matched)
      StacSearchCollection(metadata, items)
    }
  }

  def insertStacItem(item: StacItem): ConnectionIO[StacItem] = {
    val projectedGeometry = Projected(item.geometry, 4326)
    val itemExtent        = Projected(item.geometry.getEnvelope, 4326)

    val insertFragment = fr"""
      INSERT INTO collection_items (id, extent, geom, item)
      VALUES
      (${item.id}, $itemExtent, $projectedGeometry, $item)
      """
    insertFragment.update
      .withUniqueGeneratedKeys[StacItem]("item")
  }

}
