package com.azavea.franklin.api.endpoints

import io.circe._
import io.circe.generic.semiauto._

case class DeleteMessage(numberDeleted: Int)

object DeleteMessage {
  implicit val encDeleteMessage: Encoder[DeleteMessage] = deriveEncoder
  implicit val decDeleteMessage: Decoder[DeleteMessage] = deriveDecoder
}
