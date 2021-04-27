package com.azavea.franklin.api.endpoints

import cats.effect._
import com.azavea.franklin.api._
import com.azavea.franklin.datamodel._
import fs2.{Stream => FS2Stream}
import io.circe._
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir._
import sttp.tapir._
import sttp.tapir.codec.refined._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

case class AcceptHeader(v: Option[String]) {
  private val mediaTypeOption = v.map(_.split(","))

  lazy val acceptJson = mediaTypeOption match {
    case Some(acceptStrings) => acceptStrings.contains("application/json")
    case _                   => true
  }
}

class LandingPageEndpoints[F[_]: Sync] {

  val base = endpoint.in("")

  val landingPageEndpoint: Endpoint[Unit, Unit, LandingPage, Fs2Streams[F]] =
    base.get
      .out(
        jsonBody[LandingPage]
      )
      .description("STAC Service Provided via [franklin](https://github.com/azavea/franklin)")
      .name("landingPage")

  val conformanceEndpoint: Endpoint[Unit, Unit, Conformance, Fs2Streams[F]] =
    endpoint.get
      .in("conformance")
      .out(jsonBody[Conformance])
      .description(
        "A list of all conformance classes specified in a standard that the server conforms to"
      )
      .name("conformance")

  val endpoints = List(conformanceEndpoint, landingPageEndpoint)

}
