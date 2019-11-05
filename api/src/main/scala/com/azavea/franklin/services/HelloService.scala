package com.azavea.franklin.api.services

import cats._
import com.azavea.franklin.api.endpoints._
import com.azavea.franklin.api.error._
import cats.effect._
import io.circe._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import tapir.server.http4s._

class HelloService[F[_]: Sync](implicit contextShift: ContextShift[F]) extends Http4sDsl[F] {

  def greet(name: String): F[Either[HelloError, Json]] = {
    name match {
      case "throwme" =>
        Applicative[F].pure { Left(HelloError("Oh no an invalid input")) }
      case s =>
        Applicative[F].pure {
          Right(Json.obj("message" -> Json.fromString(s"Hello, $s")))
        }
    }
  }

  val routes: HttpRoutes[F] = HelloEndpoints.greetEndpoint.toRoutes(greet _)
}
