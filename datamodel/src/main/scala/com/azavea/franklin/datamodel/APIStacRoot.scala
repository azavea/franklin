package com.azavea.franklin.datamodel

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object APIStacRoot {
  implicit val stacRootEncoder = deriveEncoder[APIStacRoot]
  implicit val stacRootDecoder = deriveDecoder[APIStacRoot]
}

case class APIStacRoot(
    id: NonEmptyString,
    title: NonEmptyString,
    description: NonEmptyString,
    links: List[Link]
)
