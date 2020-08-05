package com.azavea.franklin.api

import cats.effect.Sync
import com.azavea.franklin.api.endpoints.AcceptHeader
import fs2.Stream
import io.circe.Json
import io.circe._

package object services {

  def handleOut[F[_]: Sync](
      html: String,
      json: Json,
      accept: AcceptHeader
  ): Either[Unit, (String, Stream[F, Byte])] = {
    accept.acceptJson match {
      case true => {
        println("JSON!!!!")
        val bytes = Stream.emits(json.noSpaces.getBytes())
        Right(("application/json", bytes))
      }
      case _ => {
        println("HTML!!!!")
        val bytes = Stream.emits(html.getBytes())
        Right(("text/html", bytes))
      }
    }
  }
}
