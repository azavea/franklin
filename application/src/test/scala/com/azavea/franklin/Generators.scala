package com.azavea.franklin

import java.time.Instant

import org.scalacheck.cats.implicits._
import cats.implicits._
import com.azavea.franklin.datamodel._
import com.azavea.franklin.database.SearchFilters
import org.scalacheck._
import com.azavea.stac4s._
import geotrellis.vector.{Geometry, Point, Polygon}
import org.scalacheck.Arbitrary.arbitrary
import com.azavea.franklin.api.schemas._

trait Generators {

  private def twoDimBboxGen: Gen[TwoDimBbox] = {
    (arbitrary[Double], arbitrary[Double], arbitrary[Double], arbitrary[Double])
      .mapN(TwoDimBbox.apply _)
  }

  private def threeDimBboxGen: Gen[ThreeDimBbox] =
    (
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double],
      arbitrary[Double]
    ).mapN(ThreeDimBbox.apply _)

  private def bboxGen: Gen[Bbox] =
    Gen.oneOf(twoDimBboxGen.widen, threeDimBboxGen.widen)

  private def instantGen: Gen[Instant] = arbitrary[Int] map { x =>
    Instant.now.plusMillis(x.toLong)
  }

  private def rectangleGen: Gen[Geometry] =
    for {
      lowerX <- Gen.choose[Double](-80, 80)
      lowerY <- Gen.choose[Double](-80, 80)
    } yield {
      Polygon(
        Point(lowerX, lowerY),
        Point(lowerX + 100, lowerY),
        Point(lowerX + 100, lowerY + 100),
        Point(lowerX, lowerY + 100),
        Point(lowerX, lowerY)
      )
    }

  private def temporalExtentGen: Gen[TemporalExtent] = {
    (arbitrary[Instant], arbitrary[Instant]).tupled
      .map {
        case (start, end) =>
          TemporalExtent(start, end)
      }
  }

  implicit val arbInstant: Arbitrary[Instant] = Arbitrary { instantGen }

  implicit val arbGeometry: Arbitrary[Geometry] = Arbitrary { rectangleGen }

  private def searchFiltersGen: Gen[SearchFilters] = {
    (
      Gen.option(bboxGen),
      Gen.option(temporalExtentGen),
      Gen.option(rectangleGen),
      Gen.const(List.empty[String]),
      Gen.const(List.empty[String]),
      Gen.option(Gen.choose(1, 20)),
      Gen.const[Option[String]](None),
      Gen.const(Map.empty[String, List[Query]])
    ).mapN(SearchFilters.apply)
  }

  def searchFiltersToParams(filters: SearchFilters): String = {
    val bboxString           = ("bbox", filters.bbox.map(bboxCodec.encode))
    val temporalExtentString = ("datetime", filters.datetime.map(teCodec.encode))
    val collections          = ("collections", Some(csvListCodec.encode(filters.collections)))
    val items                = ("items", Some(csvListCodec.encode(filters.items)))
    val limit                = ("limit", filters.limit.map(_.toString))
    val next                 = ("next", filters.next)

    List(bboxString, temporalExtentString, collections, items, limit, next)
      .flatMap {
        case (k, Some(v)) => Some(s"$k=$v")
        case _            => None
      }
      .mkString("&")
  }
  implicit val arbSearchFilters = Arbitrary { searchFiltersGen }
}
