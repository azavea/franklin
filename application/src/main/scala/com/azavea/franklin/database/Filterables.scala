package com.azavea.franklin.database

import cats.implicits._
import com.azavea.franklin.datamodel._
import com.azavea.stac4s.TemporalExtent
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.refined.implicits._
import doobie.{Query => _, _}
import geotrellis.vector.Projected

trait FilterHelpers {

  implicit class TemporalExtentWithFilter(temporalExtent: TemporalExtent) {

    def toFilterFragment: Option[Fragment] = {
      temporalExtent.value match {
        case Some(start) :: Some(end) :: _ =>
          Some(
            fr"(item #>> '{properties, datetime}') :: TIMESTAMPTZ >= $start AND (item #>> '{properties, datetime}') :: TIMESTAMPTZ <= $end"
          )
        case Some(start) :: _ =>
          Some(fr"(item #>> '{properties, datetime}') :: TIMESTAMPTZ >= $start")
        case _ :: Some(end) :: _ =>
          Some(fr"(item #>> '{properties, datetime}') :: TIMESTAMPTZ <= $end")
        case _ => None
      }
    }
  }

  implicit class QueryWithFilter(query: Query) {

    def toFilterFragment(field: String) = {
      val fieldFragment        = Fragment.const(s"item -> 'properties' ->> '$field'")
      val numericFieldFragment = fieldFragment ++ fr":: float8"
      query match {
        case EqualsString(value) =>
          fieldFragment ++ fr"= $value"
        case EqualsNumber(value) =>
          numericFieldFragment ++ fr"= $value"
        case NotEqualToString(value) =>
          fieldFragment ++ fr"<> $value"
        case NotEqualToNumber(value) =>
          numericFieldFragment ++ fr"<> $value"
        case GreaterThan(floor) =>
          numericFieldFragment ++ fr"> $floor"
        case GreaterThanEqual(floor) =>
          numericFieldFragment ++ fr"$floor"
        case LessThan(ceiling) =>
          numericFieldFragment ++ fr"< $ceiling"
        case LessThanEqual(ceiling) =>
          numericFieldFragment ++ fr"<= $ceiling"
        case StartsWith(prefix) =>
          Fragment.const(s"starts_with(item ->> '$field', '$prefix')")
        case EndsWith(postfix) =>
          Fragment.const(s"right(item ->> '$field', ${postfix.value.length})") ++ fr"= $postfix"
        case Contains(substring) =>
          Fragment.const(s"strpos(item ->> '$field', '$substring') > 0")
        case InStrings(values) => Fragments.in(fieldFragment, values)
        case InNumbers(values) => Fragments.in(numericFieldFragment, values)
      }
    }
  }
}

trait Filterables extends GeotrellisWktMeta with FilterHelpers {

  implicit val fragmentFilter: Filterable[Any, doobie.Fragment] =
    Filterable[Any, Fragment] { fragment: Fragment => List(Some(fragment)) }

  implicit def maybeTFilter[T](
      implicit filterable: Filterable[Any, T]
  ): Filterable[Any, Option[T]] = Filterable[Any, Option[T]] {
    case None        => List.empty[Option[Fragment]]
    case Some(thing) => filterable.toFilters(thing)
  }

  implicit def listTFilter[T](
      implicit filterable: Filterable[Any, T]
  ): Filterable[Any, List[T]] =
    Filterable[Any, List[T]] { someFilterables: List[T] =>
      {
        someFilterables
          .map(filterable.toFilters)
          .foldLeft(List.empty[Option[Fragment]])(_ ++ _)
      }
    }

  implicit val searchFilter: Filterable[Any, SearchFilters] =
    Filterable[Any, SearchFilters] { searchFilters: SearchFilters =>
      val collectionsFilter: Option[Fragment] = searchFilters.collections.toNel
        .map(collections => Fragments.in(fr"item ->> 'collection'", collections))
      val idFilter: Option[Fragment] =
        searchFilters.items.toNel.map(ids => Fragments.in(fr"id", ids))

      val geometryFilter: Option[Fragment] =
        searchFilters.intersects map (g => fr"ST_INTERSECTS(geom, ${Projected(g, 4326)})")

      val bboxFilter: Option[Fragment] = searchFilters.bbox.flatMap { bbox =>
        bbox.toExtent match {
          case Left(_) => None
          case Right(extent) =>
            Some(fr"ST_INTERSECTS(geom, ${Projected(extent.toPolygon(), 4326)})")
        }
      }
      val temporalExtentFilter: Option[Fragment] =
        searchFilters.datetime.flatMap(_.toFilterFragment)

      val queryExtFilter =
        (searchFilters.query.map {
          case (k, queries) =>
            Fragments.and(
              queries.map(_.toFilterFragment(k)): _*
            )
        }).toList map { Some(_) }

      List(collectionsFilter, idFilter, geometryFilter, bboxFilter, temporalExtentFilter) ++ queryExtFilter
    }
}

object Filterables extends Filterables
