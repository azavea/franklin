package com.azavea.franklin.api.services

import cats.effect._
import com.azavea.franklin.datamodel.LandingPage
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.server.middleware._
import org.http4s.server.{Router, Server => HTTP4sServer}
import sttp.tapir.docs.openapi._
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.swagger.http4s.SwaggerHttp4s

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext

import java.util.concurrent.Executors

class StaticService[F[_]: Sync](blocker: Blocker)(implicit cs: ContextShift[F])
    extends Http4sDsl[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case req @ GET -> path =>
      StaticFile
        .fromResource(s"/assets$path", blocker, Some(req))
        .getOrElseF(NotFound())
  }
}
