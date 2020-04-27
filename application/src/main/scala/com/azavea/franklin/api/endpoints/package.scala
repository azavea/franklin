package com.azavea.franklin.api

import eu.timepit.refined._
import eu.timepit.refined.api._
import eu.timepit.refined.numeric._

import sttp.tapir._
import io.circe._
import sttp.tapir.json.circe._

package object endpoints {

  val acceptHeaderInput: EndpointInput[AcceptHeader] = header[String]("Accept").mapTo(AcceptHeader)
  val jsonOrHtmlOutput: EndpointOutput[JsonOrHtmlOutput] = header[String]("content-type").and(jsonBody[Option[Json]]).and(plainBody[Option[String]])

}
