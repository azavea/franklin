package com.azavea.franklin.api.commands

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.effect.{ContextShift, ExitCode, IO}
import cats.implicits._
import com.azavea.franklin.crawler.CatalogStacImport
import com.azavea.franklin.crawler.StacItemImporter
import com.monovore.decline._
import doobie.Transactor
import doobie.free.connection.{rollback, setAutoCommit, unit}
import doobie.util.transactor.Strategy
import org.flywaydb.core.Flyway

object Commands {

  final case class RunMigrations(databaseConfig: DatabaseConfig)

  final case class RunServer(apiConfig: ApiConfig, dbConfig: DatabaseConfig)

  final case class RunItemsImport(
      collectionId: String,
      itemUris: NonEmptyList[String],
      config: DatabaseConfig,
      dryRun: Boolean
  )

  final case class RunCatalogImport(
      catalogRoot: String,
      config: DatabaseConfig,
      dryRun: Boolean
  )

  private def runItemsImportOpts(
      implicit sync: Sync[IO],
      cs: ContextShift[IO]
  ): Opts[RunItemsImport] =
    Opts.subcommand("import-items", "Import STAC items into an existing collection") {
      (Options.collectionID, Options.stacItems(sync), Options.databaseConfig, Options.dryRun)
        .mapN(RunItemsImport)
    }

  private def runCatalogImportOpts(implicit cs: ContextShift[IO]): Opts[RunCatalogImport] =
    Opts.subcommand("import-catalog", "Import a STAC catalog") {
      (
        Options.catalogRoot,
        Options.databaseConfig,
        Options.dryRun
      ).mapN(RunCatalogImport)
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

  def runStacItemImport(
      collectionId: String,
      itemUris: NonEmptyList[String],
      config: DatabaseConfig,
      dryRun: Boolean
  )(implicit cs: ContextShift[IO]) = {
    val xa = config.getTransactor(dryRun)
    new StacItemImporter(collectionId, itemUris).runIO(xa)
  }

  def runCatalogImport(
      stacCatalog: String,
      config: DatabaseConfig,
      dryRun: Boolean
  )(
      implicit contextShift: ContextShift[IO]
  ): IO[Unit] = {
    val xa = config.getTransactor(dryRun)
    new CatalogStacImport(stacCatalog).runIO(xa)
  }

  def applicationCommand(implicit cs: ContextShift[IO]): Command[Product] =
    Command("", "Your Friendly Neighborhood OGC API - Features and STAC Web Service") {
      runServerOpts orElse runMigrationsOpts orElse runCatalogImportOpts orElse runItemsImportOpts
    }

}
