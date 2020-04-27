package com.azavea.franklin.api.endpoints

import io.circe._
import sttp.tapir._
import sttp.tapir.json.circe._
import com.azavea.franklin.api._

case class AcceptHeader(v: String) {
  private val mediaTypeStrings = v.split(",")

  lazy val acceptJson = mediaTypeStrings.contains("application/json")

}

object LandingPageEndpoints {

  val base = endpoint.in("")

  val landingPageEndpoint: Endpoint[AcceptHeader, Unit, JsonOrHtmlOutput, Nothing] =
    base.get
      .in(acceptHeaderInput)
      .out(header[String]("content-type"))
      .out(jsonBody[Option[Json]])
      .out(plainBody[Option[String]])
      .description("STAC Service Provided via [franklin](https://github.com/azavea/franklin)")
      .name("landingPage")

  val conformanceEndpoint: Endpoint[AcceptHeader, Unit, JsonOrHtmlOutput, Nothing] = endpoint.get
    .in("conformance")
    .in(acceptHeaderInput)
    .out(header[String]("content-type"))
    .out(jsonBody[Option[Json]])
    .out(plainBody[Option[String]])
    .description(
      "A list of all conformance classes specified in a standard that the server conforms to"
    )
    .name("conformance")

  val endpoints = List(conformanceEndpoint, landingPageEndpoint)

}
