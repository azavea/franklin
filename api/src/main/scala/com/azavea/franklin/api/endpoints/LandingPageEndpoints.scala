package com.azavea.franklin.api.endpoints

import io.circe._
import tapir._
import tapir.json.circe._

object LandingPageEndpoints {

  val base = endpoint.in("")

  val landingPageEndpoint: Endpoint[Unit, Unit, Json, Nothing] =
    base.get
      .out(jsonBody[Json])
      .description("STAC Service Provided via [franklin](https://github.com/azavea/franklin)")
      .name("landingPage")

  val conformanceEndpoint: Endpoint[Unit, Unit, Json, Nothing] = endpoint
    .in("conformance")
    .get
    .out(jsonBody[Json])
    .description(
      "A list of all conformance classes specified in a standard that the server conforms to"
    )
    .name("conformance")

  val endpoints = List(landingPageEndpoint, conformanceEndpoint)

}
