package com.azavea.franklin.api

import io.circe.Json
import com.azavea.franklin.api.endpoints.AcceptHeader

package object services {

  def handleOutput[E](html: String, json: Json, accept: AcceptHeader): Either[E, JsonOrHtmlOutput] = {
    accept.acceptJson match {
      case true => Right(("application/json", Some(json), None))
      case _ => Right(("text/html", None, Some(html)))
    }
  }

  def handleOutputRaw(html: String, json: Json, accept: AcceptHeader): JsonOrHtmlOutput = {
    accept.acceptJson match {
      case true => ("application/json", Some(json), None)
      case _ => ("text/html", None, Some(html))
    }
  }

}
