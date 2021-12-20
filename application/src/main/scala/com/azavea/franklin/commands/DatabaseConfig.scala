package com.azavea.franklin.api.commands

import cats.effect.Async
import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Resource
import com.zaxxer.hikari.HikariConfig
import doobie.free.connection.{rollback, setAutoCommit, unit}
import doobie.util.transactor.Strategy
import doobie.util.transactor.Transactor
import eu.timepit.refined.types.numeric._

import scala.concurrent.ExecutionContext

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

sealed abstract class DatabaseConfig {
  val jdbcUrl: String
  val driver: String = "org.postgresql.Driver"

  def getTransactor(dryRun: Boolean)(implicit contextShift: ContextShift[IO]): Transactor[IO]

  def toHikariConfig: HikariConfig
}

case object DatabaseConfig {

  final case class FromComponents(
      dbUser: String,
      dbPass: String,
      dbHost: String,
      dbPort: PosInt,
      dbName: String
  ) extends DatabaseConfig {
    val jdbcUrl = s"jdbc:postgresql://$dbHost:$dbPort/$dbName"

    def getTransactor(dryRun: Boolean)(implicit contextShift: ContextShift[IO]) = {
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

    def toHikariConfig: HikariConfig = {
      val config = new HikariConfig()
      config.setJdbcUrl(jdbcUrl)
      config.setUsername(dbUser)
      config.setPassword(dbPass)
      config.setDriverClassName(driver)
      config
    }
  }

  final case class FromConnectionString(
      jdbcUrl: String
  ) extends DatabaseConfig {

    def getTransactor(dryRun: Boolean)(implicit contextShift: ContextShift[IO]): Transactor[IO] = {
      val blockingEc = ExecutionContext.fromExecutor(
        Executors.newCachedThreadPool(
          new ThreadFactory {
            def newThread(r: Runnable): Thread = {
              val th = new Thread(r)
              th.setName(s"doobie-blocking-ec-${th.getId}")
              th.setDaemon(true)
              th
            }
          }
        )
      )

      Transactor.strategy.set(
        Transactor.fromDriverManager[IO](
          driver,
          jdbcUrl,
          Blocker.liftExecutionContext(blockingEc)
        ),
        if (dryRun) {
          Strategy.default.copy(before = setAutoCommit(false), after = rollback, always = unit)
        } else { Strategy.default }
      )
    }

    def toHikariConfig: HikariConfig = {
      val config = new HikariConfig()
      config.setDriverClassName(driver)
      config.setJdbcUrl(jdbcUrl)
      config
    }
  }

}
