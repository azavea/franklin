package com.azavea.franklin.datamodel

import cats.data.NonEmptyList
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.syntax._

sealed abstract class Query

case class EqualsString(value: NonEmptyString)             extends Query
case class EqualsNumber(value: Double)                     extends Query
case class NotEqualToString(value: NonEmptyString)         extends Query
case class NotEqualToNumber(value: Double)                 extends Query
case class GreaterThan(floor: Double)                      extends Query
case class GreaterThanEqual(floor: Double)                 extends Query
case class LessThan(ceiling: Double)                       extends Query
case class LessThanEqual(ceiling: Double)                  extends Query
case class StartsWith(prefix: NonEmptyString)              extends Query
case class EndsWith(postfix: NonEmptyString)               extends Query
case class Contains(substring: NonEmptyString)             extends Query
case class InStrings(values: NonEmptyList[NonEmptyString]) extends Query
case class InNumbers(values: NonEmptyList[Double])         extends Query

object Query {

  private def fromString(s: String, f: NonEmptyString => Query) =
    NonEmptyString.from(s).toOption.map(f)

  private def errMessage(operator: String, json: Json): String =
    s"Cannot construct `$operator` query with $json"

  def queriesFromMap(unparsed: Map[String, Json]): Either[String, List[Query]] =
    (unparsed.toList traverse {
      case (op @ "eq", json) =>
        Either.fromOption(json.asString flatMap { fromString(_, EqualsString.apply) } orElse {
          json.asNumber map { num => EqualsNumber(num.toDouble) }
        }, errMessage(op, json))
      case (op @ "neq", json) =>
        Either.fromOption(json.asString flatMap { fromString(_, NotEqualToString.apply) } orElse {
          json.asNumber map { num => NotEqualToNumber(num.toDouble) }
        }, errMessage(op, json))
      case (op @ "lt", json) =>
        Either.fromOption(json.asNumber map { num => LessThan(num.toDouble) }, errMessage(op, json))
      case (op @ "lte", json) =>
        Either.fromOption(
          json.asNumber map { num => LessThanEqual(num.toDouble) },
          errMessage(op, json)
        )
      case (op @ "gt", json) =>
        Either.fromOption(
          json.asNumber map { num => GreaterThan(num.toDouble) },
          errMessage(op, json)
        )
      case (op @ "gte", json) =>
        Either.fromOption(
          json.asNumber map { num => GreaterThanEqual(num.toDouble) },
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
          json.asArray flatMap { vec =>
            // maybe it's a non-empty list of strings
            (vec traverse { js =>
              js.asString flatMap { NonEmptyString.from(_).toOption }
            } flatMap { _.toNev } map { vec =>
              InStrings(vec.toNonEmptyList)
            }) orElse
              // or maybe it's a non-empty list of numbers
              (vec traverse { js =>
                js.asNumber map { _.toDouble }
              } flatMap { _.toNev } map { vec => InNumbers(vec.toNonEmptyList) })
          },
          errMessage(op, json)
        )
      case (k, _) => Left(s"$k is not a valid operator")
    })

  implicit val encQuery: Encoder[List[Query]] = new Encoder[List[Query]] {

    def apply(queries: List[Query]): Json =
      Map(
        (queries map {
          case EqualsString(value)     => "eq"         -> value.asJson
          case EqualsNumber(value)     => "eq"         -> value.asJson
          case NotEqualToString(value) => "neq"        -> value.asJson
          case NotEqualToNumber(value) => "neq"        -> value.asJson
          case GreaterThan(floor)      => "gt"         -> floor.asJson
          case GreaterThanEqual(floor) => "gte"        -> floor.asJson
          case LessThan(ceiling)       => "lt"         -> ceiling.asJson
          case LessThanEqual(ceiling)  => "lte"        -> ceiling.asJson
          case StartsWith(prefix)      => "startsWith" -> prefix.asJson
          case EndsWith(postfix)       => "endsWith"   -> postfix.asJson
          case Contains(substring)     => "contains"   -> substring.asJson
          case InStrings(values)       => "in"         -> values.asJson
          case InNumbers(values)       => "in"         -> values.asJson
        }): _*
      ).asJson
  }

  implicit val decQueries: Decoder[List[Query]] = Decoder[JsonObject].emap { jsonObj =>
    queriesFromMap(jsonObj.toMap)
  }
}
