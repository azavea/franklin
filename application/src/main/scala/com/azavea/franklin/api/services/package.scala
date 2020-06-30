package com.azavea.franklin.api

import cats.effect.Sync
import com.azavea.franklin.api.endpoints.AcceptHeader
import fs2.Stream
import io.circe.Json
import io.circe._
import io.circe.fs2._

package object services {

  def handleOut[F[_]: Sync](html: String, json: Json, accept: AcceptHeader): Either[Unit, (String, Stream[F, Byte])] = {
    accept.acceptJson match {
      case true => {
        val bytes = Stream.emits(json.noSpaces.getBytes())
        Right(("application/json", bytes))
      }
      case _ => {
        val bytes = Stream.emits(html.getBytes())
        Right(("text/html", bytes))
      }
    }
  }

}
