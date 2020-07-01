package com.azavea.franklin.api.endpoints

import cats.effect._
import com.azavea.franklin.api._
import com.azavea.franklin.datamodel._
import fs2.{Stream => FS2Stream}
import io.circe._
import sttp.tapir._
import sttp.tapir._
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

  val landingPageEndpoint
      : Endpoint[AcceptHeader, Unit, (String, FS2Stream[F, Byte]), FS2Stream[F, Byte]] =
    base.get
      .in(acceptHeaderInput)
      .out(header[String]("content-type"))
      .out(streamBody[FS2Stream[F, Byte]](schemaFor[LandingPage], CodecFormat.Json()))
      .description("STAC Service Provided via [franklin](https://github.com/azavea/franklin)")
      .name("landingPage")

  val conformanceEndpoint
      : Endpoint[AcceptHeader, Unit, (String, FS2Stream[F, Byte]), FS2Stream[F, Byte]] =
    endpoint.get
      .in("conformance")
      .in(acceptHeaderInput)
      .out(header[String]("content-type"))
      .out(streamBody[FS2Stream[F, Byte]](schemaFor[Conformance], CodecFormat.Json()))
      .description(
        "A list of all conformance classes specified in a standard that the server conforms to"
      )
      .name("conformance")

  val endpoints = List(conformanceEndpoint, landingPageEndpoint)

}
