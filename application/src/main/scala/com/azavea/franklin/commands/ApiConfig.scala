package com.azavea.franklin.commands

import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}
import eu.timepit.refined.types.string.NonEmptyString

case class ApiConfig(
    publicPort: PosInt,
    internalPort: PosInt,
    host: String,
    path: Option[String],
    scheme: String,
    defaultLimit: NonNegInt,
    enableTransactions: Boolean
) {

  def getHost(port: PosInt, host: String, scheme: String, path: String): NonEmptyString = {
    (port.value, scheme) match {
      case (443, "https") => NonEmptyString.unsafeFrom(s"$scheme://$host$path")
      case (80, "http")   => NonEmptyString.unsafeFrom(s"$scheme://$host$path")
      case _              => NonEmptyString.unsafeFrom(s"$scheme://$host:$port$path")
    }
  }

  val apiHost: NonEmptyString =
    getHost(publicPort, host, scheme, path map { s =>
      s"/${s.stripPrefix("/").stripSuffix("/")}"
    } getOrElse "")

}
