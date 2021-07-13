package com.azavea.franklin.api.services

import cats.data.NonEmptyList
import cats.syntax.all._
import cats.{Monoid, Semigroup}
import com.azavea.franklin.database.SearchFilters
import com.azavea.stac4s.{ItemDatetime, StacCollection, StacItem, TemporalExtent, TwoDimBbox}
import geotrellis.vector.Extent
import io.circe.syntax._

import java.time.Instant

object FiltersFor {

  implicit val searchFilterMonoid: Monoid[SearchFilters] = new Monoid[SearchFilters] {

    def combine(x: SearchFilters, y: SearchFilters): SearchFilters = SearchFilters(
      x.bbox orElse y.bbox,
      x.datetime orElse y.datetime,
      x.intersects orElse y.intersects,
      x.collections |+| y.collections,
      x.items |+| y.items,
      x.limit orElse y.limit,
      x.query |+| y.query,
      x.next orElse y.next
    )

    def empty: SearchFilters = SearchFilters(
      None,
      None,
      None,
      List.empty,
      List.empty,
      None,
      Map.empty,
      None
    )
  }

  def bboxFilterFor(item: StacItem): SearchFilters = {
    val itemGeomBbox = item.geometry.getEnvelopeInternal
    SearchFilters(
      Some(
        TwoDimBbox(
          itemGeomBbox.getMinX,
          itemGeomBbox.getMinY,
          itemGeomBbox.getMaxX,
          itemGeomBbox.getMaxY
        )
      ),
      None,
      None,
      Nil,
      Nil,
      None,
      Map.empty,
      None
    )
  }

  def timeFilterFor(item: StacItem): SearchFilters = {
    val temporalExtent = item.properties.datetime match {
      case ItemDatetime.PointInTime(instant) =>
        TemporalExtent(instant.minusSeconds(60), Some(instant.plusSeconds(60)))
      case ItemDatetime.TimeRange(start, end) =>
        val milli = start.toEpochMilli % 3
        if (milli == 0) {
          // test start and end with full overlap
          TemporalExtent(start.minusSeconds(60), Some(end.plusSeconds(60)))
        } else if (milli == 1) {
          // test start before the range start with open end
          TemporalExtent(start.minusSeconds(60), None)
        } else {
          // test end after the range end with open start
          TemporalExtent(None, end.plusSeconds(60))
        }
    }
    SearchFilters(
      None,
      Some(temporalExtent),
      None,
      Nil,
      Nil,
      None,
      Map.empty,
      None
    )
  }

  def geomFilterFor(item: StacItem): SearchFilters = SearchFilters(
    None,
    None,
    Some(item.geometry),
    Nil,
    Nil,
    None,
    Map.empty,
    None
  )

  def collectionFilterFor(collection: StacCollection): SearchFilters =
    SearchFilters(
      None,
      None,
      None,
      List(collection.id),
      Nil,
      None,
      Map.empty,
      None
    )

  def itemFilterFor(item: StacItem): SearchFilters = SearchFilters(
    None,
    None,
    None,
    Nil,
    List(item.id),
    None,
    Map.empty,
    None
  )

  def bboxFilterExcluding(item: StacItem): SearchFilters = {
    val itemGeomBbox = item.geometry.getEnvelopeInternal()
    val rhs          = itemGeomBbox.getMinX() - 1
    val bottom       = itemGeomBbox.getMinY() - 1
    val newBbox      = TwoDimBbox(rhs - 1, bottom - 1, rhs, bottom)
    SearchFilters(
      Some(newBbox),
      None,
      None,
      Nil,
      Nil,
      None,
      Map.empty,
      None
    )
  }

  def timeFilterExcluding(item: StacItem): SearchFilters = {
    val temporalExtent = item.properties.datetime match {
      case ItemDatetime.PointInTime(instant) =>
        TemporalExtent(instant.minusSeconds(60), Some(instant.minusSeconds(30)))
      case ItemDatetime.TimeRange(start, end) =>
        val milli = start.toEpochMilli % 3
        if (milli == 0) {
          // test no intersection with range
          TemporalExtent(start.minusSeconds(60), Some(start.minusSeconds(30)))
        } else if (milli == 1) {
          // test start after the range end with open end
          TemporalExtent(end.plusSeconds(60), None)
        } else {
          // test end before the range start with open start
          TemporalExtent(None, start.minusSeconds(60))
        }
    }
    SearchFilters(
      None,
      Some(temporalExtent),
      None,
      Nil,
      Nil,
      None,
      Map.empty,
      None
    )
  }

  def geomFilterExcluding(item: StacItem): SearchFilters = {
    val itemGeomBbox = item.geometry.getEnvelopeInternal()
    val rhs          = itemGeomBbox.getMinX() - 1
    val bottom       = itemGeomBbox.getMinY() - 1
    val newGeom      = Extent(rhs - 1, bottom - 1, rhs, bottom).toPolygon
    SearchFilters(
      None,
      None,
      Some(newGeom),
      Nil,
      Nil,
      None,
      Map.empty,
      None
    )
  }

  def collectionFilterExcluding(collection: StacCollection): SearchFilters = SearchFilters(
    None,
    None,
    None,
    // prepending not will guarantee the id doesn't match
    List("not " ++ collection.id),
    Nil,
    None,
    Map.empty,
    None
  )

  def itemFilterExcluding(item: StacItem): SearchFilters = SearchFilters(
    None,
    None,
    None,
    Nil,
    // prepending not will guarantee the id doesn't match
    List("not " ++ item.id),
    None,
    Map.empty,
    None
  )

  def inclusiveFilters(collection: StacCollection, item: StacItem): SearchFilters = {
    val filters: NonEmptyList[SearchFilters] = NonEmptyList
      .of(
        bboxFilterFor(item),
        timeFilterFor(item),
        geomFilterFor(item),
        collectionFilterFor(collection),
        itemFilterFor(item)
      )
    filters.combineAll
  }

  def inclusiveFilters(collection: StacCollection): SearchFilters = {
    val filters: NonEmptyList[Option[SearchFilters]] =
      NonEmptyList.of(collectionFilterFor(collection).some)
    val concatenated = filters.combineAll
    // guaranteed to succeed, since most of the filters are being converted into options
    // just to cooperate with timeFilterFor
    concatenated.get
  }
}
