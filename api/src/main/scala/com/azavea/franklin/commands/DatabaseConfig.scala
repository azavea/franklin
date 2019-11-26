package com.azavea.franklin.api.commands

import eu.timepit.refined.types.numeric._

final case class DatabaseConfig(
    dbUser: String,
    dbPass: String,
    dbHost: String,
    dbPort: PosInt,
    dbName: String
) {
  val jdbcUrl = s"jdbc:postgresql://$dbHost:$dbPort/$dbName"
  val driver  = "org.postgresql.Driver"
}
