package com.azavea.franklin.database

import cats.effect._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.util.transactor.Transactor

import scala.util.Properties

object PgstacConfig {
  var jdbcDriver: String = "org.postgresql.Driver"

  val jdbcNoDBUrl: String =
    Properties.envOrElse(
      "POSTGRES_URL",
      "jdbc:postgresql://localhost:5439/"
    )

  val jdbcDBName: String =
    Properties.envOrElse("POSTGRES_NAME", "franklin")
  val jdbcUrl: String = jdbcNoDBUrl + jdbcDBName
  val dbUser: String  = Properties.envOrElse("POSTGRES_USER", "franklin")

  val dbPassword: String =
    Properties.envOrElse("POSTGRES_PASSWORD", "franklin")

  val dbStatementTimeout: String =
    Properties.envOrElse("POSTGRES_STATEMENT_TIMEOUT", "30000")

  val dbMaximumPoolSize: Int =
    Properties.envOrElse("POSTGRES_DB_POOL_SIZE", "5").toInt

  def nonHikariTransactor[F[_]: Async](databaseName: String)(implicit cs: ContextShift[F]) = {
    Transactor.fromDriverManager[F](
      "org.postgresql.Driver",
      jdbcNoDBUrl + databaseName,
      dbUser,
      dbPassword
    )
  }


  println(s"jdbcUrl $jdbcUrl")
  println(s"dbuser $dbUser")
  println(s"dbpass $dbPassword")
  println(s"jdbcdriver $jdbcDriver")


  val hikariConfig = new HikariConfig()
  hikariConfig.setPoolName("pgstac-pool")
  hikariConfig.setMaximumPoolSize(dbMaximumPoolSize)
  hikariConfig.setConnectionInitSql(
    s"SET statement_timeout = ${dbStatementTimeout};"
  )
  hikariConfig.setJdbcUrl(jdbcUrl)
  hikariConfig.setUsername(dbUser)
  hikariConfig.setPassword(dbPassword)
  hikariConfig.setDriverClassName(jdbcDriver)

  val hikariDS = new HikariDataSource(hikariConfig)
}
