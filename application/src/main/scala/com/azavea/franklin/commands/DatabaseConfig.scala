package com.azavea.franklin.api.commands

import cats.effect.ContextShift
import cats.effect.IO
import doobie.free.connection.{rollback, setAutoCommit, unit}
import doobie.util.transactor.Strategy
import doobie.util.transactor.Transactor
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

  def getTransactor(dryRun: Boolean)(implicit cs: ContextShift[IO]) = {
    Transactor.strategy.set(
      Transactor.fromDriverManager[IO](
        driver,
        jdbcUrl,
        dbUser,
        dbPass
      ),
      if (dryRun) {
        Strategy.default.copy(before = setAutoCommit(false), after = rollback, always = unit)
      } else { Strategy.default }
    )
  }
}
