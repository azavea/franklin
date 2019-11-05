package com.azavea.franklin.api

import cats.effect._
import io.circe._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

object HelloService extends Http4sDsl[IO] {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / name => {
      Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))
    }
  }
}
