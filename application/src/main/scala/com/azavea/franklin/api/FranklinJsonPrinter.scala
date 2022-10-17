package com.azavea.franklin.api

import io.circe.Printer
import sttp.tapir.json.circe._

// This custom json printing implementation for tapir is based
//  on https://tapir.softwaremill.com/en/latest/endpoint/json.html#circe
object FranklinJsonPrinter extends TapirJsonCirce {
  // NOTE: the contents of this object should be imported for *tapir endpoints* rather than
  //  http4s services. e.g. SearchEndpoints.scala but not SearchService.scala
  override def jsonPrinter: Printer = Printer.spaces2.copy(dropNullValues = true)
}
