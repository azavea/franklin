package com.azavea.franklin.database

import cats.syntax.all._
import com.azavea.franklin.datamodel._
import com.azavea.franklin.extensions.validation.ExtensionName
import com.azavea.stac4s.types.TemporalExtent
import doobie.implicits._
import doobie.postgres.circe.jsonb.implicits._
import doobie.refined.implicits._
import doobie.{Query => _, _}
import geotrellis.vector.Projected
import io.circe.syntax._

trait FilterHelpers {

  implicit class TemporalExtentWithFilter(temporalExtent: TemporalExtent) {

    def toFilterFragment: Option[Fragment] = {
      temporalExtent.value match {
        case Some(start) :: Some(end) :: _ =>
          Some(fr"(datetime >= $start AND datetime <= $end)")
        case Some(start) :: _ =>
          Some(fr"datetime >= $start")
        case _ :: Some(end) :: _ =>
          Some(fr"datetime <= $end")
        case _ => None
      }
    }
  }

  implicit class QueryWithFilter(query: Query) {

    def toFilterFragment(field: String) = {
      val fieldFragment = Fragment.const(s"item -> 'properties' -> '$field'")
      query match {
        case Equals(value) =>
          fieldFragment ++ fr"= $value"
        case NotEqualTo(value) =>
          fieldFragment ++ fr"<> $value"
        case GreaterThan(floor) =>
          fieldFragment ++ fr"> $floor"
        case GreaterThanEqual(floor) =>
          fieldFragment ++ fr">= $floor"
        case LessThan(ceiling) =>
          fieldFragment ++ fr"< $ceiling"
        case LessThanEqual(ceiling) =>
          fieldFragment ++ fr"<= $ceiling"
        case StartsWith(prefix) =>
          Fragment.const(s"starts_with(item ->> '$field', '$prefix')")
        case EndsWith(postfix) =>
          Fragment.const(s"right(item ->> '$field', ${postfix.value.length})") ++ fr"= $postfix"
        case Contains(substring) =>
          Fragment.const(s"strpos(item ->> '$field', '$substring') > 0")
        case In(values)       => Fragments.in(fieldFragment, values)
        case Superset(values) => fieldFragment ++ fr"@> ${values.asJson}"
      }
    }
  }
}

trait Filterables extends GeotrellisWktMeta with FilterHelpers {

  implicit val fragmentFilter: Filterable[Any, Fragment] =
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

  implicit val paginationTokenFilter: Filterable[Any, PaginationToken] =
    Filterable[Any, PaginationToken] { paginationToken: PaginationToken =>
      List(Some(fr"""
        created_at > ${paginationToken.timestampAtLeast} OR
        (created_at = ${paginationToken.timestampAtLeast} AND serial_id > ${paginationToken.serialIdGreaterThan})
      """))
    }

  implicit val searchFilter: Filterable[Any, SearchFilters] =
    Filterable[Any, SearchFilters] { searchFilters: SearchFilters =>
      val collectionsFilter: Option[Fragment] = searchFilters.collections.toNel
        .map(collections => Fragments.in(fr"collection", collections))
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

      List(collectionsFilter, idFilter, geometryFilter, bboxFilter, temporalExtentFilter) ++ queryExtFilter ++ Filterable
        .summon[
          Any,
          Option[PaginationToken]
        ]
        .toFilters(searchFilters.next)
    }

  implicit val extensionNamesFilter: Filterable[Any, List[ExtensionName]] =
    Filterable[Any, List[ExtensionName]] { extensions =>
      List(
        extensions.toNel map { extensions => fr"item -> 'stac_extensions' @> ${extensions.asJson}" }
      )
    }
}

object Filterables extends Filterables
