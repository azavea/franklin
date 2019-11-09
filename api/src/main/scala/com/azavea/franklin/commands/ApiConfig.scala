package com.azavea.franklin.api.commands

import eu.timepit.refined.types.numeric.PosInt

case class ApiConfig(publicPort: PosInt, internalPort: PosInt, host: String, scheme: String) {

  val apiHost: String = (publicPort.value, scheme) match {
    case (443, "https") => s"$scheme://$host"
    case (80, "http")   => s"$scheme://$host"
    case _              => s"$scheme://$host:$publicPort"
  }

}
