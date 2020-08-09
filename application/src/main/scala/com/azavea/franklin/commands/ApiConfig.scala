package com.azavea.franklin.api.commands

import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}
import eu.timepit.refined.types.string.NonEmptyString

case class ApiConfig(
    publicPort: PosInt,
    internalPort: PosInt,
    host: String,
    path: Option[String],
    scheme: String,
    defaultLimit: NonNegInt,
    enableTransactions: Boolean,
    enableTiles: Boolean
) {

  val apiHost: NonEmptyString =
    getHost(publicPort, host, scheme, path map { s =>
      s"/${s.stripPrefix("/").stripSuffix("/")}"
    } getOrElse "")

}
