package com.azavea.franklin.datamodel

import io.circe._
import io.circe.generic.semiauto._

case class Conformance(conformsTo: List[NonEmptyString])

object Conformance {
  implicit val conformanceDecoder: Decoder[Conformance] = deriveDecoder[Conformance]
  implicit val conformanceEncoder: Encoder[Conformance] = deriveEncoder[Conformance]
}
