package com.azavea.franklin.api

import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString

package object commands {

  def getHost(port: PosInt, host: String, scheme: String, path: String): NonEmptyString = {
    (port.value, scheme) match {
      case (443, "https") => NonEmptyString.unsafeFrom(s"$scheme://$host$path")
      case (80, "http")   => NonEmptyString.unsafeFrom(s"$scheme://$host$path")
      case _              => NonEmptyString.unsafeFrom(s"$scheme://$host:$port$path")
    }
  }

}
