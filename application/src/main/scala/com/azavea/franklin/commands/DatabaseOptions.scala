package com.azavea.franklin.api.commands

import cats.effect._
import cats.syntax.all._
import com.lightbend.emoji.ShortCodes.Defaults._
import com.lightbend.emoji.ShortCodes.Implicits._
import com.monovore.decline.Opts
import com.monovore.decline._
import com.monovore.decline.refined._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.types.numeric._

import scala.util.Try

trait DatabaseOptions {

  private val databaseOptionDefault = "franklin"

  private val databasePortDefault = PosInt(5432)
  private val databasePortHelp    = s"Port to connect to database on. Default: '$databasePortDefault'."

  private val databasePort = (Opts.option[PosInt]("db-port", help = databasePortHelp) orElse Opts
    .env[PosInt]("DB_PORT", help = databasePortHelp)) withDefault (databasePortDefault)

  private val databaseHostHelp = "Database host to connect to."

  private val databaseHost = (Opts.option[String]("db-host", help = databaseHostHelp) orElse Opts
    .env[String]("DB_HOST", help = databaseHostHelp)) withDefault ("database.service.internal")

  private val databaseNameHelp = s"Database name to connect to. Default: '$databaseOptionDefault'."

  private val databaseName = (Opts.option[String]("db-name", help = databaseNameHelp) orElse Opts
    .env[String]("DB_NAME", help = databaseNameHelp)) withDefault (databaseOptionDefault)

  private val databasePasswordHelp = s"Database password to use. Default: '$databaseOptionDefault'."

  private val databasePassword =
    (Opts.option[String]("db-password", help = databasePasswordHelp) orElse Opts
      .env[String]("DB_PASSWORD", help = databasePasswordHelp)) withDefault (databaseOptionDefault)

  private val databaseUserHelp =
    s"User to connect with database with. Default: '$databaseOptionDefault'."

  private val databaseUser = Opts.option[String]("db-user", help = databaseUserHelp) orElse Opts
    .env[String]("DB_USER", help = databaseUserHelp) withDefault (databaseOptionDefault)

  private val databaseConnectionStringHelp =
    "Complete JDBC connection string to use to connect to database"

  private val connectionString = Opts.option[String](
    "db-connection-string",
    help = databaseConnectionStringHelp
  ) orElse Opts.env[String]("DB_CONNECTION_STRING", help = databaseConnectionStringHelp)

  def databaseConfig(implicit contextShift: ContextShift[IO]): Opts[DatabaseConfig] =
    ((connectionString map { DatabaseConfig.FromConnectionString }) orElse (
      databaseUser,
      databasePassword,
      databaseHost,
      databasePort,
      databaseName
    ).mapN { DatabaseConfig.FromComponents })
      .validate(
        e":boom: Unable to connect to database - please ensure database is configured and listening at entered port"
      ) { config =>
        val xa =
          config.getTransactor(true)
        val select = Try {
          fr"SELECT 1".query[Int].unique.transact(xa).unsafeRunSync()
        }
        select.toEither match {
          case Right(_) => true
          case Left(e) =>
            println(s"Connection failure: ${e}")
            println(s"Failure details:\n${e.getMessage()}")
            e.printStackTrace()
            false
        }
      }
}
