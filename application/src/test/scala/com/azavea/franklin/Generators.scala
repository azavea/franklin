package com.azavea.franklin

import java.time.Instant

import cats.data.NonEmptyVector
import org.scalacheck.cats.implicits._
import cats.implicits._
import com.azavea.franklin.datamodel._
import com.azavea.franklin.database.SearchFilters
import org.scalacheck._
import com.azavea.stac4s._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.syntax._
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

  private def nonEmptyAlphaStringGen: Gen[String] =
    Gen.nonEmptyListOf(Gen.alphaChar) map { _.mkString("") }

  private def nonEmptyAlphaRefinedStringGen: Gen[NonEmptyString] =
    nonEmptyAlphaStringGen map NonEmptyString.unsafeFrom

  private def nonEmptyVectorGen[T](g: Gen[T]): Gen[NonEmptyVector[T]] =
    Gen.nonEmptyContainerOf[Vector, T](g) map { NonEmptyVector.fromVectorUnsafe }

  private def queryGen: Gen[Query] = Gen.oneOf(
    nonEmptyAlphaRefinedStringGen map { s => Equals(s.asJson) },
    arbitrary[Int] map { n => Equals(n.asJson) },
    nonEmptyAlphaRefinedStringGen map { s => NotEqualTo(s.asJson) },
    arbitrary[Int] map { n => NotEqualTo(n.asJson) },
    Gen.const(Map("field" -> "value").asJson) map Equals.apply,
    Gen.const(Map("field" -> "value").asJson) map NotEqualTo.apply,
    arbitrary[Double] map { n => GreaterThan(n.asJson) },
    arbitrary[Double] map { n => GreaterThanEqual(n.asJson) },
    arbitrary[Double] map { n => LessThan(n.asJson) },
    arbitrary[Double] map { n => LessThanEqual(n.asJson) },
    nonEmptyAlphaStringGen map { s => GreaterThan(s.asJson) },
    nonEmptyAlphaStringGen map { s => GreaterThanEqual(s.asJson) },
    nonEmptyAlphaStringGen map { s => LessThan(s.asJson) },
    nonEmptyAlphaStringGen map { s => LessThanEqual(s.asJson) },
    nonEmptyAlphaRefinedStringGen map StartsWith.apply,
    nonEmptyAlphaRefinedStringGen map EndsWith.apply,
    nonEmptyAlphaRefinedStringGen map Contains.apply,
    nonEmptyVectorGen(nonEmptyAlphaRefinedStringGen map { _.asJson }) map In.apply,
    nonEmptyVectorGen(arbitrary[Int] map { _.asJson }) map In.apply,
    nonEmptyVectorGen(
      (nonEmptyAlphaStringGen, nonEmptyAlphaStringGen).tupled.map({
        case (k, v) => Map(k -> v).asJson
      })
    ) map In.apply,
    nonEmptyVectorGen(arbitrary[Int] map { _.asJson }) map Superset.apply,
    nonEmptyVectorGen(
      (nonEmptyAlphaStringGen, nonEmptyAlphaStringGen).tupled.map({
        case (k, v) => Map(k -> v).asJson
      })
    ) map Superset.apply
  )

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
      Gen.mapOf((nonEmptyAlphaStringGen, Gen.nonEmptyListOf(queryGen)).tupled)
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
