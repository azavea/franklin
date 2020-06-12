package com.azavea.franklin.database

import com.azavea.franklin.datamodel.PaginationToken
import eu.timepit.refined.types.numeric.NonNegInt
import io.circe.generic.semiauto._
import io.circe.refined._

final case class Page(limit: NonNegInt, next: Option[PaginationToken])

object Page {
  implicit val pageEncoder = deriveEncoder[Page]
  implicit val pageDecoder = deriveDecoder[Page]
}
