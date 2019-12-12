package com.azavea.franklin.datamodel

import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object SearchMetadata {
  implicit val searchMetadataEncoder = deriveEncoder[SearchMetadata]
  implicit val searchMetadataDecoder = deriveDecoder[SearchMetadata]
}

case class SearchMetadata(
    next: Option[NonEmptyString],
    returned: Int,
    limit: Option[Int],
    matched: Int
)
