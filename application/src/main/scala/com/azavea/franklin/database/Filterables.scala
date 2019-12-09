package com.azavea.franklin.database

import cats.implicits._
import doobie._
import doobie.implicits._
import geotrellis.server.stac.TemporalExtent
import geotrellis.vector.Projected

trait FilterHelpers {

  implicit class TemporalExtentWithFilter(temporalExtent: TemporalExtent) {

    def toFilterFragment: Option[Fragment] = {
      temporalExtent.value match {
        case Some(start) :: Some(end) :: _ =>
          Some(
            fr"(item #>> '{properties, datetime}') :: TIMESTAMP >= $start AND (item #>> '{properties, datetime}') :: TIMESTAMP <= $end")
        case Some(start) :: _ =>
          Some(fr"(item #>> '{properties, datetime}') :: TIMESTAMP >= $start")
        case _ :: Some(end) :: _ =>
          Some(fr"(item #>> '{properties, datetime}') :: TIMESTAMP <= $end")
        case _ => None
      }
    }
  }
}

trait Filterables extends GeotrellisWktMeta with FilterHelpers {

  implicit val fragmentFilter: Filterable[Any, doobie.Fragment] =
    Filterable[Any, Fragment] { fragment: Fragment =>
      List(Some(fragment))
    }

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
        .map(
          collections => Fragments.in(fr"item #>> '{properties, collection}'", collections)
        )
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
      List(collectionsFilter, idFilter, geometryFilter, bboxFilter, temporalExtentFilter)
    }
}

object Filterables extends Filterables
