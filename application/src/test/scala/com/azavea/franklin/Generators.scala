package com.azavea.franklin

import cats.data.NonEmptyVector
import cats.syntax.all._
import com.azavea.franklin.database.SearchFilters
import com.azavea.franklin.datamodel._
import com.azavea.stac4s._
import com.azavea.stac4s.testing._
import com.azavea.stac4s.testing.{JvmInstances, TestInstances}
import com.azavea.stac4s.types._
import eu.timepit.refined.scalacheck.NumericInstances
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.{Geometry, Point, Polygon}
import io.circe.syntax._
import io.circe.testing.{ArbitraryInstances => CirceInstances}
import io.circe.{Json, JsonNumber}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck._
import org.scalacheck.cats.implicits._

import java.time.Instant

trait Generators extends NumericInstances with CirceInstances {

  private def paginationTokenGen: Gen[PaginationToken] = {
    (arbitrary[Instant], arbitrary[PosInt]).mapN(PaginationToken.apply)
  }

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

  private def nonEmptyAlphaStringGen: Gen[String] =
    Gen.listOfN(15, Gen.alphaChar) map { _.mkString("") }

  

  

  private def jsonLitGen: Gen[Json] =
    Gen.oneOf(
      Gen.const(Json.Null),
      nonEmptyAlphaStringGen map { Json.fromString },
      Gen.chooseNum[Double](-500, 500) map { _.asJson }
    )

  private def comparisonGen: Gen[Comparison] = Gen.oneOf(
    jsonLitGen map {
      Comparison.BinaryLiteralComparison
    },
    Gen.choose(1, 3) flatMap { nComponents =>
      (1 to nComponents).toList traverse { _ =>
        nonEmptyAlphaStringGen
      } map { pathComponents => Comparison.BinaryJsonPathComparison(pathComponents.mkString(".")) }
    }
  )

  private case class QueryCounts(
      eq: Int = 0,
      lt: Int = 0,
      gt: Int = 0,
      lte: Int = 0,
      gte: Int = 0
  ) {
    def max = List(eq, lt, gt, lte, gte).max
  }

  private def xyRestGen: Gen[(CQLFilter, CQLFilter, List[CQLFilter])] =
    (comparisonFilterGen, comparisonFilterGen, Gen.listOfN(1, comparisonFilterGen)).tupled.filter({
      case (x, y, rest) => {
        (List(x, y) ++ rest)
          .foldLeft(QueryCounts())({
            case (counts, CQLFilter.GreaterThan(_))      => counts.copy(gt = counts.gt + 1)
            case (counts, CQLFilter.GreaterThanEqual(_)) => counts.copy(gte = counts.gte + 1)
            case (counts, CQLFilter.LessThan(_))         => counts.copy(lt = counts.lt + 1)
            case (counts, CQLFilter.LessThanEqual(_))    => counts.copy(lte = counts.lte + 1)
            case (counts, CQLFilter.Equals(_))           => counts.copy(eq = counts.eq + 1)
            case (counts, _)                             => counts
          })
          .max <= 1
      }
    })

  private def logicalGen: Gen[CQLBooleanExpression] =
    Gen.oneOf(
      xyRestGen map { case (x, y, rest) => CQLFilter.And(x, y, rest) },
      xyRestGen map { case (x, y, rest) => CQLFilter.Or(x, y, rest) }
    )

  private def notGen: Gen[CQLBooleanExpression] =
    logicalGen map { CQLFilter.Not(_) }

  private def comparisonFilterGen: Gen[CQLFilter] = {
    Gen.oneOf(
      comparisonGen map { CQLFilter.Equals },
      comparisonGen map { CQLFilter.LessThan },
      comparisonGen map { CQLFilter.LessThanEqual },
      comparisonGen map { CQLFilter.GreaterThan },
      comparisonGen map { CQLFilter.GreaterThanEqual }
    )
  }

  private def cqlFilterGen = Gen.oneOf(
    comparisonFilterGen,
    logicalGen,
    notGen
  )

  // private def queryGen: Gen[Query] = Gen.oneOf(
  //   nonEmptyAlphaRefinedStringGen map { s => Equals(s.asJson) },
  //   arbitrary[Int] map { n => Equals(n.asJson) },
  //   nonEmptyAlphaRefinedStringGen map { s => NotEqualTo(s.asJson) },
  //   arbitrary[Int] map { n => NotEqualTo(n.asJson) },
  //   Gen.const(Map("field" -> "value").asJson) map Equals.apply,
  //   Gen.const(Map("field" -> "value").asJson) map NotEqualTo.apply,
  //   arbitrary[Double] map { n => GreaterThan(n.asJson) },
  //   arbitrary[Double] map { n => GreaterThanEqual(n.asJson) },
  //   arbitrary[Double] map { n => LessThan(n.asJson) },
  //   arbitrary[Double] map { n => LessThanEqual(n.asJson) },
  //   nonEmptyAlphaStringGen map { s => GreaterThan(s.asJson) },
  //   nonEmptyAlphaStringGen map { s => GreaterThanEqual(s.asJson) },
  //   nonEmptyAlphaStringGen map { s => LessThan(s.asJson) },
  //   nonEmptyAlphaStringGen map { s => LessThanEqual(s.asJson) },
  //   nonEmptyAlphaRefinedStringGen map StartsWith.apply,
  //   nonEmptyAlphaRefinedStringGen map EndsWith.apply,
  //   nonEmptyAlphaRefinedStringGen map Contains.apply,
  //   nonEmptyVectorGen(nonEmptyAlphaRefinedStringGen map { _.asJson }) map In.apply,
  //   nonEmptyVectorGen(arbitrary[Int] map { _.asJson }) map In.apply,
  //   nonEmptyVectorGen(
  //     (nonEmptyAlphaStringGen, nonEmptyAlphaStringGen).tupled.map({
  //       case (k, v) => Map(k -> v).asJson
  //     })
  //   ) map In.apply,
  //   nonEmptyVectorGen(arbitrary[Int] map { _.asJson }) map Superset.apply,
  //   nonEmptyVectorGen(
  //     (nonEmptyAlphaStringGen, nonEmptyAlphaStringGen).tupled.map({
  //       case (k, v) => Map(k -> v).asJson
  //     })
  //   ) map Superset.apply
  // )

  implicit val arbInstant: Arbitrary[Instant] = Arbitrary { instantGen }

  implicit val arbGeometry: Arbitrary[Geometry] = Arbitrary { rectangleGen }

  implicit val arbCqlFilter: Arbitrary[CQLFilter] = Arbitrary { cqlFilterGen }

  private def searchFiltersGen: Gen[SearchFilters] = {
    (
      Gen.option(arbitrary(TestInstances.arbBbox)),
      Gen.option(arbitrary(JvmInstances.arbTemporalExtent)),
      Gen.option(rectangleGen),
      Gen.const(List.empty[String]),
      Gen.const(List.empty[String]),
      Gen.option(arbitrary[NonNegInt]),
      Gen.mapOfN(5, (nonEmptyAlphaStringGen, Gen.nonEmptyListOf(cqlFilterGen)).tupled),
      Gen.const(None)
    ).mapN(SearchFilters.apply)
  }

  implicit val arbSearchFilters   = Arbitrary { searchFiltersGen }
  implicit val arbPaginationToken = Arbitrary { paginationTokenGen }
}
