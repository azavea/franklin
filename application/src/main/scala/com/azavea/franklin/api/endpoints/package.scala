package com.azavea.franklin.api

import eu.timepit.refined._
import eu.timepit.refined.api._
import eu.timepit.refined.numeric._
import io.circe._
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.{Codec => TapirCodec}

package object endpoints {

  val acceptHeaderInput: EndpointInput[AcceptHeader] =
    header[Option[String]]("Accept").mapTo(AcceptHeader)
}
