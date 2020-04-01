package com.azavea.franklin.api

import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString

package object commands {

  def getHost(port: PosInt, host: String, scheme: String) = {
    (port.value, scheme) match {
      case (443, "https") => NonEmptyString.unsafeFrom(s"$scheme://$host")
      case (80, "http")   => NonEmptyString.unsafeFrom(s"$scheme://$host")
      case _              => NonEmptyString.unsafeFrom(s"$scheme://$host:$port")
    }
  }

}
