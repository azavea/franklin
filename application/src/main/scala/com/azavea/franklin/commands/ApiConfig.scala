package com.azavea.franklin.api.commands

import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString

case class ApiConfig(
    publicPort: PosInt,
    internalPort: PosInt,
    host: String,
    scheme: String,
    enableTransactions: Boolean
) {

  val apiHost: NonEmptyString = (publicPort.value, scheme) match {
    case (443, "https") => NonEmptyString.unsafeFrom(s"$scheme://$host")
    case (80, "http")   => NonEmptyString.unsafeFrom(s"$scheme://$host")
    case _              => NonEmptyString.unsafeFrom(s"$scheme://$host:$publicPort")
  }

}
