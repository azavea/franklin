package com.azavea.franklin.datamodel

import io.circe._
import io.circe.generic.semiauto._


final case class SortDefinition(
    field: String,
    direction: String
) {

  def toQP =
    if (direction == "asc") s"+${field}"
    else s"-${field}"
}

object SortDefinition {

  def fromQP(s: String): SortDefinition =
    if (s.startsWith(("-"))) {
      SortDefinition(s.drop(1), "desc")
    } else if (s.startsWith("+")) {
      SortDefinition(s.drop(1), "asc")
    } else {
      SortDefinition(s, "asc")
    }

  implicit val sortDefinitionDecoder: Decoder[SortDefinition] = deriveDecoder[SortDefinition]
  implicit val sortDefinitionEncoder: Encoder[SortDefinition] = deriveEncoder[SortDefinition]
}