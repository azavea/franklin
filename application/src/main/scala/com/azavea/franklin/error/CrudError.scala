package com.azavea.franklin.error

import com.azavea.stac4s.StacItem
import io.circe.generic.semiauto._
import io.circe.{Codec => _, _}

sealed abstract class CrudError

case class NotFound(msg: String = "Not found") extends CrudError

object NotFound {
  implicit val encNotFound: Encoder[NotFound] = deriveEncoder
  implicit val decNotFound: Decoder[NotFound] = deriveDecoder
}

case class ValidationError(msg: String) extends CrudError

object ValidationError {
  implicit val encValidationError: Encoder[ValidationError] = deriveEncoder
  implicit val decValidationError: Decoder[ValidationError] = deriveDecoder
}

case class MidAirCollision(msg: String, currentEtag: String, currentItem: StacItem)
    extends CrudError

object MidAirCollision {
  implicit val encMidAirCollision: Encoder[MidAirCollision] = deriveEncoder
  implicit val decMidAirCollision: Decoder[MidAirCollision] = deriveDecoder
}

case class InvalidPatch(msg: String, patch: Json) extends CrudError

object InvalidPatch {
  implicit val encInvalidPatch: Encoder[InvalidPatch] = deriveEncoder
  implicit val decInvalidPatch: Decoder[InvalidPatch] = deriveDecoder
}
