package com.azavea.franklin.api.services

import cats.data.NonEmptyList
import cats.syntax.all._
import cats.{Monoid, Semigroup}
import com.azavea.franklin.database.SearchParameters
import com.azavea.stac4s.{ItemDatetime, StacCollection, StacItem, TemporalExtent, TwoDimBbox}
import geotrellis.vector.Extent
import io.circe.syntax._

import java.time.Instant

object FiltersFor {

  implicit val searchFilterMonoid: Monoid[SearchParameters] = new Monoid[SearchParameters] {

    def combine(x: SearchParameters, y: SearchParameters): SearchParameters = SearchParameters(
      x.bbox orElse y.bbox,
      x.datetime orElse y.datetime,
      x.intersects orElse y.intersects,
      x.collections |+| y.collections,
      x.items |+| y.items,
      x.limit orElse y.limit,
      x.query |+| y.query,
      x.next orElse y.next
    )

    def empty: SearchParameters = SearchParameters(
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

  def bboxFilterFor(item: StacItem): SearchParameters = {
    val itemGeomBbox = item.geometry.getEnvelopeInternal
    SearchParameters(
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

  def timeFilterFor(item: StacItem): SearchParameters = {
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
    SearchParameters(
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

  def geomFilterFor(item: StacItem): SearchParameters = SearchParameters(
    None,
    None,
    Some(item.geometry),
    Nil,
    Nil,
    None,
    Map.empty,
    None
  )

  def collectionFilterFor(collection: StacCollection): SearchParameters =
    SearchParameters(
      None,
      None,
      None,
      List(collection.id),
      Nil,
      None,
      Map.empty,
      None
    )

  def itemFilterFor(item: StacItem): SearchParameters = SearchParameters(
    None,
    None,
    None,
    Nil,
    List(item.id),
    None,
    Map.empty,
    None
  )

  def bboxFilterExcluding(item: StacItem): SearchParameters = {
    val itemGeomBbox = item.geometry.getEnvelopeInternal()
    val rhs          = itemGeomBbox.getMinX() - 1
    val bottom       = itemGeomBbox.getMinY() - 1
    val newBbox      = TwoDimBbox(rhs - 1, bottom - 1, rhs, bottom)
    SearchParameters(
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

  def timeFilterExcluding(item: StacItem): SearchParameters = {
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
    SearchParameters(
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

  def geomFilterExcluding(item: StacItem): SearchParameters = {
    val itemGeomBbox = item.geometry.getEnvelopeInternal()
    val rhs          = itemGeomBbox.getMinX() - 1
    val bottom       = itemGeomBbox.getMinY() - 1
    val newGeom      = Extent(rhs - 1, bottom - 1, rhs, bottom).toPolygon
    SearchParameters(
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

  def collectionFilterExcluding(collection: StacCollection): SearchParameters = SearchParameters(
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

  def itemFilterExcluding(item: StacItem): SearchParameters = SearchParameters(
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

  def inclusiveFilters(collection: StacCollection, item: StacItem): SearchParameters = {
    val filters: NonEmptyList[SearchParameters] = NonEmptyList
      .of(
        bboxFilterFor(item),
        timeFilterFor(item),
        geomFilterFor(item),
        collectionFilterFor(collection),
        itemFilterFor(item)
      )
    filters.combineAll
  }

  def inclusiveFilters(collection: StacCollection): SearchParameters = {
    val filters: NonEmptyList[Option[SearchParameters]] =
      NonEmptyList.of(collectionFilterFor(collection).some)
    val concatenated = filters.combineAll
    // guaranteed to succeed, since most of the filters are being converted into options
    // just to cooperate with timeFilterFor
    concatenated.get
  }
}
