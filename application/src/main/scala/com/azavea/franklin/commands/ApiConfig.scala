package com.azavea.franklin.api.commands

import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString

case class ApiConfig(
    publicPort: PosInt,
    internalPort: PosInt,
    host: String,
    scheme: String,
    enableTransactions: Boolean,
    enableTiles: Boolean
) {

  val apiHost: NonEmptyString = getHost(publicPort, host, scheme)

}
