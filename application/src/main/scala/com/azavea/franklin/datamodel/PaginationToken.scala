package com.azavea.franklin.datamodel

import com.azavea.stac4s.meta._
import eu.timepit.refined.types.numeric.PosInt
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import io.circe.refined._

import java.time.Instant

final case class PaginationToken(
    timestampAtLeast: Instant,
    serialIdGreaterThan: PosInt
)

object PaginationToken {
  implicit val dec: Decoder[PaginationToken] = deriveDecoder
  implicit val enc: Encoder[PaginationToken] = deriveEncoder
}
