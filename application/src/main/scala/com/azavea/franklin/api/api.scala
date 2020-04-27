package com.azavea.franklin

import io.circe._

package object api {
  type JsonOrHtmlOutput = (String, Option[Json], Option[String])
}
