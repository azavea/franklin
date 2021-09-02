package com.azavea.franklin.datamodel

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object Context {
  implicit val searchMetadataEncoder = deriveEncoder[Context]
  implicit val searchMetadataDecoder = deriveDecoder[Context]
}

case class Context(
    limit: Int,
    returned: Int,
    matched: Int
)
