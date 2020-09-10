package com.azavea.franklin.datamodel

import cats.data.NonEmptyVector
import cats.syntax.all._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.syntax._

sealed abstract class Query

case class Equals(value: Json)                    extends Query
case class NotEqualTo(value: Json)                extends Query
case class GreaterThan(floor: Json)               extends Query
case class GreaterThanEqual(floor: Json)          extends Query
case class LessThan(ceiling: Json)                extends Query
case class LessThanEqual(ceiling: Json)           extends Query
case class StartsWith(prefix: NonEmptyString)     extends Query
case class EndsWith(postfix: NonEmptyString)      extends Query
case class Contains(substring: NonEmptyString)    extends Query
case class In(values: NonEmptyVector[Json])       extends Query
case class Superset(values: NonEmptyVector[Json]) extends Query

object Query {

  private def fromString(s: String, f: NonEmptyString => Query): Option[Query] =
    NonEmptyString.from(s).toOption.map(f)

  private def fromStringOrNum(js: Json, f: Json => Query): Option[Query] =
    js.asNumber map { _ =>
      f(js)
    } orElse { js.asString map { _ => f(js) } }

  private def errMessage(operator: String, json: Json): String =
    s"Cannot construct `$operator` query with $json"

  def queriesFromMap(unparsed: Map[String, Json]): Either[String, List[Query]] =
    (unparsed.toList traverse {
      case (op @ "eq", json)  => Right(Equals(json))
      case (op @ "neq", json) => Right(NotEqualTo(json))
      case (op @ "lt", json) =>
        Either.fromOption(fromStringOrNum(json, LessThan.apply), errMessage(op, json))
      case (op @ "lte", json) =>
        Either.fromOption(
          fromStringOrNum(json, LessThanEqual.apply),
          errMessage(op, json)
        )
      case (op @ "gt", json) =>
        Either.fromOption(
          fromStringOrNum(json, GreaterThan.apply),
          errMessage(op, json)
        )
      case (op @ "gte", json) =>
        Either.fromOption(
          fromStringOrNum(json, GreaterThanEqual.apply),
          errMessage(op, json)
        )
      case (op @ "startsWith", json) =>
        Either.fromOption(
          json.asString flatMap { fromString(_, StartsWith.apply) },
          errMessage(op, json)
        )
      case (op @ "endsWith", json) =>
        Either.fromOption(
          json.asString flatMap { fromString(_, EndsWith.apply) },
          errMessage(op, json)
        )
      case (op @ "contains", json) =>
        Either.fromOption(
          json.asString flatMap { fromString(_, Contains.apply) },
          errMessage(op, json)
        )
      case (op @ "in", json) =>
        Either.fromOption(
          json.asArray flatMap { _.toNev } map { vec => In(vec) },
          errMessage(op, json)
        )
      case (op @ "superset", json) =>
        Either.fromOption(
          json.asArray flatMap { _.toNev } map { vec => Superset(vec) },
          errMessage(op, json)
        )
      case (k, _) => Left(s"$k is not a valid operator")
    })

  implicit val encQuery: Encoder[List[Query]] = new Encoder[List[Query]] {

    def apply(queries: List[Query]): Json =
      Map(
        (queries map {
          case Equals(value)           => "eq"         -> value.asJson
          case NotEqualTo(value)       => "neq"        -> value.asJson
          case GreaterThan(floor)      => "gt"         -> floor.asJson
          case GreaterThanEqual(floor) => "gte"        -> floor.asJson
          case LessThan(ceiling)       => "lt"         -> ceiling.asJson
          case LessThanEqual(ceiling)  => "lte"        -> ceiling.asJson
          case StartsWith(prefix)      => "startsWith" -> prefix.asJson
          case EndsWith(postfix)       => "endsWith"   -> postfix.asJson
          case Contains(substring)     => "contains"   -> substring.asJson
          case In(values)              => "in"         -> values.asJson
          case Superset(values)        => "superset"   -> values.asJson
        }): _*
      ).asJson
  }

  implicit val decQueries: Decoder[List[Query]] = Decoder[JsonObject].emap { jsonObj =>
    queriesFromMap(jsonObj.toMap)
  }
}
