package com.azavea.franklin.datamodel

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object SearchMetadata {
  implicit val searchMetadataEncoder = deriveEncoder[SearchMetadata]
  implicit val searchMetadataDecoder = deriveDecoder[SearchMetadata]
}

case class SearchMetadata(
    next: Option[NonEmptyString],
    returned: Int,
    limit: Int,
    matched: Int
)
