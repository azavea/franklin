package com.azavea.franklin.datamodel

import cats.Semigroup
import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.traverse._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}

sealed abstract class Comparison
// BinComparisons are values that can be on the right-hand-side of a
// binary comparison predicate, i.e. the RHS of
// https://github.com/radiantearth/stac-api-spec/blob/eba41352ec9bda279ac266313bc036d2e6a6faae/fragments/filter/cql_schema.json#L75-L83
sealed abstract class BinComparison extends Comparison

object Comparison {
  final case class BinaryLiteralComparison(value: Json)           extends BinComparison
  final case class BinaryJsonPathComparison(propertyName: String) extends BinComparison

  implicit val decBinaryLiteralComparison: Decoder[BinaryLiteralComparison] =
    Decoder[Json] map { BinaryLiteralComparison }

  implicit val decBinaryJsonPathComparison: Decoder[BinaryJsonPathComparison] = deriveDecoder

  implicit val decBinComparison: Decoder[BinComparison] =
    decBinaryJsonPathComparison.widen or decBinaryLiteralComparison.widen

  implicit val decComparison: Decoder[Comparison] = decBinComparison.widen

  implicit val encBinaryLiteralComparison: Encoder[BinaryLiteralComparison] = _.value

  implicit val encBinaryJsonPathComparison: Encoder[BinaryJsonPathComparison] = deriveEncoder

  implicit val encBinComparison: Encoder[BinComparison] = {
    case lit @ BinaryLiteralComparison(_)       => lit.asJson
    case jsonPath @ BinaryJsonPathComparison(_) => jsonPath.asJson
  }

  implicit val encComparison: Encoder[Comparison] = {
    case bin: BinComparison => bin.asJson
  }

}

sealed abstract class CQLFilter
sealed abstract class CQLBooleanExpression extends CQLFilter

object CQLFilter {
  // binary operators
  final case class Equals(value: Comparison)           extends CQLFilter
  final case class LessThan(ceiling: Comparison)       extends CQLFilter
  final case class LessThanEqual(ceiling: Comparison)  extends CQLFilter
  final case class GreaterThan(floor: Comparison)      extends CQLFilter
  final case class GreaterThanEqual(floor: Comparison) extends CQLFilter

  // logical operators
  final case class And(x: CQLFilter, y: CQLFilter, rest: List[CQLFilter])
      extends CQLBooleanExpression

  final case class Or(x: CQLFilter, y: CQLFilter, rest: List[CQLFilter])
      extends CQLBooleanExpression
  final case class Not(x: CQLFilter) extends CQLBooleanExpression

  def fromCmp(json: Json, f: Comparison => CQLFilter) =
    json.as[Comparison].bimap(_.getMessage, f(_))

  def atLeastTwoDecoder(
      f: (CQLFilter, CQLFilter, List[CQLFilter]) => CQLBooleanExpression,
      js: Json
  ) =
    js.as[List[CQLFilter]]
      .fold(
        err => Either.left[String, CQLFilter](err.getMessage), {
          case x :: y :: rest => Either.right(f(x, y, rest))
          case _ =>
            Either.left(
              "and operator requires at least two array entries"
            )
        }
      )

  implicit val decCQLFilter: Decoder[CQLFilter] = Decoder[JsonObject].emap { jsonObj =>
    val asMap = jsonObj.toMap

    (asMap.get("eq") map { js =>
      fromCmp(js, Equals(_))
    } orElse (asMap.get("lt") map { js => fromCmp(js, LessThan(_)) }) orElse (asMap.get("lte") map {
      js =>
        fromCmp(js, LessThanEqual(_))
    }) orElse (asMap.get("gt") map { js => fromCmp(js, GreaterThan(_)) }) orElse (asMap.get("gte") map {
      js =>
        fromCmp(js, GreaterThanEqual(_))
    }) orElse (asMap.get("and") map { js => atLeastTwoDecoder(And.apply, js) }) orElse (
      asMap.get("or") map { js => atLeastTwoDecoder(Or.apply, js) }
    ) orElse (asMap.get("not") map { js => js.as[CQLFilter].leftMap(_.getMessage) map { Not(_) } }))
      .fold(
        Either.left[String, CQLFilter](
          "No keys were found in the object that could be converted to CQL filters"
        )
      )(
        identity _
      )

  }

  implicit val encCQLFilter: Encoder[CQLFilter] = { cqlFilter =>
    Map(
      (cqlFilter match {
        case Equals(v)           => "eq"  -> v.asJson
        case LessThan(v)         => "lt"  -> v.asJson
        case LessThanEqual(v)    => "lte" -> v.asJson
        case GreaterThan(v)      => "gt"  -> v.asJson
        case GreaterThanEqual(v) => "gte" -> v.asJson
        case And(x, y, rest) =>
          "and" -> (List(x, y) ++ rest).asJson
        case Or(x, y, rest) =>
          "or" -> (List(x, y) ++ rest).asJson
        case Not(x) => "not" -> x.asJson
      })
    ).asJson
  }

}
