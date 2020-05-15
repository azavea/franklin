package com.azavea.franklin.datamodel

import cats.data.NonEmptyList
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._

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

  def queriesFromMap(unparsed: Map[String, Json]) = unparsed flatMap {
    case ("eq", json) =>
      json.asString map { fromString(_, EqualsString.apply) } orElse {
        json.asNumber map { num => EqualsNumber(num.toDouble) }
      }
    case ("neq", json) =>
      json.asString map { fromString(_, NotEqualToString.apply) } orElse {
        json.asNumber map { num => NotEqualToNumber(num.toDouble) }
      }
    case ("lt", json)         => json.asNumber map { num => LessThan(num.toDouble) }
    case ("lte", json)        => json.asNumber map { num => LessThanEqual(num.toDouble) }
    case ("gt", json)         => json.asNumber map { num => GreaterThan(num.toDouble) }
    case ("gte", json)        => json.asNumber map { num => GreaterThanEqual(num.toDouble) }
    case ("startsWith", json) => json.asString flatMap { fromString(_, StartsWith.apply) }
    case ("endsWith", json)   => json.asString flatMap { fromString(_, EndsWith.apply) }
    case ("contains", json)   => json.asString flatMap { fromString(_, Contains.apply) }
    case ("in", json) =>
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
      }
    case _ => None
  }
}
