package com.azavea.franklin.datamodel

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object Context {
  implicit val searchMetadataEncoder = deriveEncoder[Context]
  implicit val searchMetadataDecoder = deriveDecoder[Context]
}

case class Context(
    next: Option[NonEmptyString],
    returned: Int,
    limit: Option[Int],
    matched: Int
)
