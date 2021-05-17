package com.azavea.franklin.datamodel

import cats.syntax.either._
import cats.syntax.functor._
import cats.syntax.traverse._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, JsonObject}

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

object CQLFilter {
  // binary operators
  final case class Equals(value: Comparison)           extends CQLFilter
  final case class LessThan(ceiling: Comparison)       extends CQLFilter
  final case class LessThanEqual(ceiling: Comparison)  extends CQLFilter
  final case class GreaterThan(floor: Comparison)      extends CQLFilter
  final case class GreaterThanEqual(floor: Comparison) extends CQLFilter

  def fromCmp(json: Json, f: Comparison => CQLFilter) =
    json.as[Comparison].bimap(_.getMessage, f(_))

  implicit val decCQLFilter: Decoder[List[CQLFilter]] = Decoder[JsonObject].emap { jsonObj =>
    jsonObj.toMap.toList traverse {
      case (op @ "eq", json) => fromCmp(json, Equals(_))
      case (op @ "lt", json) =>
        fromCmp(json, LessThan(_))
      case (op @ "lte", json) =>
        fromCmp(json, LessThanEqual(_))
      case (op @ "gt", json) =>
        fromCmp(json, GreaterThan(_))
      case (op @ "gte", json) =>
        fromCmp(json, GreaterThanEqual(_))
      case (k, _) => Left(s"$k is not a valid CQL operator")
    }
  }

  implicit val encCQLFilteR: Encoder[List[CQLFilter]] = { cqlFilters =>
    Map(
      (cqlFilters map {
        case Equals(v)           => "eq"  -> v.asJson
        case LessThan(v)         => "lt"  -> v.asJson
        case LessThanEqual(v)    => "lte" -> v.asJson
        case GreaterThan(v)      => "gt"  -> v.asJson
        case GreaterThanEqual(v) => "gte" -> v.asJson
      }): _*
    ).asJson
  }
}
