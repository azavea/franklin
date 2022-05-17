package com.azavea.franklin.commands

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.effect.{ContextShift, ExitCode, IO}
import cats.syntax.all._
import com.monovore.decline._
import com.zaxxer.hikari.HikariDataSource
import doobie.Transactor
import doobie.free.connection.{rollback, setAutoCommit, unit}
import doobie.util.transactor.Strategy
import org.flywaydb.core.Flyway
import sttp.client.{NothingT, SttpBackend}

object Commands {

  final case class RunServer(apiConfig: ApiConfig, dbConfig: DatabaseConfig)

  private def runServerOpts(implicit cs: ContextShift[IO]): Opts[RunServer] =
    Opts.subcommand("serve", "Runs web service") {
      (Options.apiConfig, Options.databaseConfig) mapN RunServer
    }

  def applicationCommand(implicit cs: ContextShift[IO]): Command[Product] =
    Command("", "Your Friendly Neighborhood OGC API - Features and STAC Web Service") {
      runServerOpts
    }

}