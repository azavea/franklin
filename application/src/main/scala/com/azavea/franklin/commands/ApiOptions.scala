package com.azavea.franklin.api.commands

import cats.implicits._
import com.monovore.decline.Opts
import com.monovore.decline.refined._
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.numeric.PosInt

trait ApiOptions {

  private val portDefault      = PosInt(9090)
  private val externalPortHelp = s"Port users/clients hit for requests. Default: '$portDefault'."

  val externalPort = Opts
    .option[PosInt]("external-port", help = externalPortHelp) orElse
    Opts.env[PosInt]("API_EXTERNAL_PORT", help = externalPortHelp) withDefault (portDefault)

  private val internalPortHelp =
    s"Port server listens on, this will be different from 'external-port' when service is started behind a proxy. Default: '$portDefault'."

  val internalPort = Opts
    .option[PosInt](
      "internal-port",
      help = internalPortHelp
    ) orElse Opts
    .env[PosInt]("API_INTERNAL_PORT", help = internalPortHelp) withDefault (portDefault)

  private val apiHostDefault = "localhost"

  private val apiHostHelp =
    s"Hostname Franklin is hosted it (e.g. localhost). Default: '$apiHostDefault'."

  val apiHost = Opts
    .option[String]("api-host", help = apiHostHelp) orElse
    Opts.env[String]("API_HOST", help = apiHostHelp) withDefault (apiHostDefault)

  private val apiPathHelp = "Path component for root of Franklin instance (e.g. /stac/api)."

  val apiPath = Opts
    .option[String](
      "api-path",
      help = apiPathHelp
    ) orElse
    Opts.env[String]("API_PATH", help = apiPathHelp) orNone

  private val apiSchemeDefault = "http"

  private val apiSchemeHelp =
    s"Scheme server is exposed to end users with. Default: '$apiSchemeDefault'."

  val apiScheme =
    Opts
      .option[String]("api-scheme", help = apiSchemeHelp) orElse
      Opts.env[String]("API_SCHEME", help = apiSchemeHelp) withDefault ("http")
  validate("Scheme must be either 'http' or 'https'")(s => (s == "http" || s == "https"))

  private val defaultLimitDefault = NonNegInt(30)

  private val defaultLimitHelp =
    s"Default limit for items returned in paginated responses. Default: '$defaultLimitDefault'."

  private val defaultLimit =
    Opts
      .option[NonNegInt]("default-limit", help = defaultLimitHelp) orElse
      Opts.env[NonNegInt]("API_DEFAULT_LIMIT", help = defaultLimitHelp) withDefault (defaultLimitDefault)

  private val enableTransactionsHelp =
    "Whether to respond to transaction requests, like adding or updating items. Default: 'false'."

  private val enableTransactions =
    Opts
      .flag(
        "with-transactions",
        help = enableTransactionsHelp
      ) orElse
      Opts.env[Boolean]("API_WITH_TRANSACTIONS", help = enableTransactionsHelp) orFalse

  private val enableTilesHelp = "Whether to include tile endpoints. Default: 'false'."

  private val enableTiles = Opts.flag("with-tiles", help = enableTilesHelp) orElse
    Opts.env[Boolean]("API_WITH_TILES", help = enableTilesHelp) orFalse

  private val runMigrationsHelp = "Run migrations before the API server starts. Default: 'false'."

  private val runMigrations =
    Opts.flag("run-migrations", help = runMigrationsHelp) orElse
      Opts.env[Boolean]("API_RUN_MIGRATIONS", help = runMigrationsHelp) orFalse

  val apiConfig: Opts[ApiConfig] = (
    externalPort,
    internalPort,
    apiHost,
    apiPath,
    apiScheme,
    defaultLimit,
    enableTransactions,
    enableTiles,
    runMigrations
  ) mapN ApiConfig
}
