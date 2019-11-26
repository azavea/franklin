package com.azavea.franklin.api.commands

import cats.effect.{ContextShift, ExitCode, IO}
import com.azavea.franklin.crawler.StacImport
import com.monovore.decline._
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway
import doobie.implicits._
import cats.implicits._

object Commands {

  final case class RunMigrations(databaseConfig: DatabaseConfig)

  final case class RunServer(apiConfig: ApiConfig, dbConfig: DatabaseConfig)

  final case class RunImport(catalogRoot: String, config: DatabaseConfig)

  private def runImportOpts(implicit cs: ContextShift[IO]): Opts[RunImport] =
    Opts.subcommand("import", "Import a STAC catalog") {
      (Options.catalogRoot, Options.databaseConfig).mapN(RunImport)
    }

  private def runMigrationsOpts(implicit cs: ContextShift[IO]): Opts[RunMigrations] =
    Opts.subcommand("migrate", "Runs migrations against database") {
      Options.databaseConfig map RunMigrations
    }

  private def runServerOpts(implicit cs: ContextShift[IO]): Opts[RunServer] =
    Opts.subcommand("serve", "Runs web service") {
      (Options.apiConfig, Options.databaseConfig) mapN RunServer
    }

  def runMigrations(dbConfig: DatabaseConfig): IO[ExitCode] = IO {
    Flyway
      .configure()
      .dataSource(
        s"${dbConfig.jdbcUrl}",
        dbConfig.dbUser,
        dbConfig.dbPass
      )
      .locations("classpath:migrations/")
      .load()
      .migrate()
    ExitCode.Success
  }

  def runImport(stacCatalog: String, config: DatabaseConfig)(
      implicit contextShift: ContextShift[IO]
  ): fs2.Stream[IO, Unit] = {
    val xa =
      Transactor.fromDriverManager[IO](config.driver, config.jdbcUrl, config.dbUser, config.dbPass)
    new StacImport(stacCatalog).run().transact(xa)
  }

  def applicationCommand(implicit cs: ContextShift[IO]): Command[Product] =
    Command("", "Your Friendly Neighborhood OGC API - Features and STAC Web Service") {
      runServerOpts orElse runMigrationsOpts orElse runImportOpts
    }

}
