package com.azavea.franklin.api.commands

import cats.implicits._
import com.monovore.decline.Opts
import com.monovore.decline.refined._
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.numeric.NonNegInt

trait ApiOptions {

  val externalPort = Opts
    .option[PosInt]("external-port", help = "Port users/clients hit for requests")
    .withDefault(PosInt(9090))

  val internalPort = Opts
    .option[PosInt](
      "internal-port",
      help =
        "Port server listens on, this will be different from 'external-port' when service is started behind a proxy"
    )
    .withDefault(PosInt(9090))

  val apiHost = Opts
    .option[String]("api-host", help = "Hostname Franklin is hosted it (e.g. localhost)")
    .withDefault("localhost")

  val apiScheme =
    Opts
      .option[String]("api-scheme", "Scheme server is exposed to end users with")
      .withDefault("http")
      .validate("Scheme must be either 'http' or 'https'")(s => (s == "http" || s == "https"))

  private val defaultLimit =
    Opts
      .option[NonNegInt]("default-limit", "Default limit for items returned in paginated responses")
      .withDefault(NonNegInt(30))

  private val enableTransactions =
    Opts
      .flag(
        "with-transactions",
        "Whether to respond to transaction requests, like adding or updating items"
      )
      .orFalse

  private val enableTiles = Opts.flag("with-tiles", "Whether to include tile endpoints").orFalse

  val apiConfig: Opts[ApiConfig] = (
    externalPort,
    internalPort,
    apiHost,
    apiScheme,
    defaultLimit,
    enableTransactions,
    enableTiles
  ) mapN ApiConfig
}
