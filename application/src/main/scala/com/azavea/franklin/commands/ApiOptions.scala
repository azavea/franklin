package com.azavea.franklin.commands

import com.azavea.franklin.datamodel.hierarchy.StacHierarchy

import cats.data.Validated
import cats.syntax.all._
import eu.timepit.refined.types.numeric.PosInt
import io.circe.parser.decode
import com.monovore.decline.Opts
import com.monovore.decline.refined._
import os.{GlobSyntax, /}

import scala.language.postfixOps
import scala.util.Try
import scala.io.Source


trait ApiOptions {

  private val portDefault      = PosInt(9090)
  private val externalPortHelp = s"Port users/clients hit for requests. Default: '$portDefault'."

  val externalPort = Opts.option[PosInt]("external-port", help = externalPortHelp) orElse Opts
    .env[PosInt]("API_EXTERNAL_PORT", help = externalPortHelp) withDefault (portDefault)

  private val internalPortHelp =
    s"Port server listens on, this will be different from 'external-port' when service is started behind a proxy. Default: '$portDefault'."

  val internalPort = Opts.option[PosInt](
    "internal-port",
    help = internalPortHelp
  ) orElse Opts
    .env[PosInt]("API_INTERNAL_PORT", help = internalPortHelp) withDefault (portDefault)

  private val apiHostDefault = "localhost"

  private val apiHostHelp =
    s"Hostname Franklin is hosted it (e.g. localhost). Default: '$apiHostDefault'."

  val apiHost = Opts.option[String]("api-host", help = apiHostHelp) orElse Opts
    .env[String]("API_HOST", help = apiHostHelp) withDefault (apiHostDefault)

  private val apiPathHelp = "Path component for root of Franklin instance (e.g. /stac/api)."

  val apiPath = (Opts.option[String](
    "api-path",
    help = apiPathHelp
  ) orElse Opts.env[String]("API_PATH", help = apiPathHelp)) orNone

  private val apiSchemeDefault = "http"

  private val apiSchemeHelp =
    s"Scheme server is exposed to end users with. Default: '$apiSchemeDefault'."

  val apiScheme = (Opts
    .option[String]("api-scheme", help = apiSchemeHelp) orElse Opts
    .env[String]("API_SCHEME", help = apiSchemeHelp))
    .withDefault("http")
    .validate(
      "Scheme must be either 'http' or 'https'"
    )(s => (s == "http" || s == "https"))

  private val defaultLimitDefault = 30

  private val defaultLimitHelp =
    s"Default limit for items returned in paginated responses. Default: '$defaultLimitDefault'."

  private val defaultLimit = Opts
    .option[Int]("default-limit", help = defaultLimitHelp) orElse Opts
    .env[Int]("API_DEFAULT_LIMIT", help = defaultLimitHelp) withDefault (defaultLimitDefault)

  private val enableTransactionsHelp =
    "Whether to respond to transaction requests, like adding or updating items. Default: 'false'."

  private val enableTransactions = Opts
    .flag(
      "with-transactions",
      help = enableTransactionsHelp
    )
    .orFalse orElse Opts
    .env[String]("API_WITH_TRANSACTIONS", help = enableTransactionsHelp, metavar = "true||false")
    .mapValidated { s =>
      Validated
        .fromTry(Try(s.toBoolean))
        .leftMap(_ => s"Expected to find a value that can convert to a Boolean, but got $s")
        .toValidatedNel
    } withDefault (false)

  private val stacHierarchyHelp =
    "Path to JSON file which defines a STAC Hierarchy. Defaults to the empty StacHierarchy."

  private def parseHierarchyFromPath(path: String): Either[io.circe.Error, StacHierarchy] = {
    val wd = os.pwd
    val jsonString = os.read(wd / os.RelPath(path.split("/"), ups=1))
    decode[StacHierarchy](jsonString)
  }

  private val stacHierarchy = Opts
    .option[String]("stac-hierarchy", help = stacHierarchyHelp)
    .mapValidated { path =>
      Validated
        .fromEither(parseHierarchyFromPath(path))
        .leftMap(_ => s"Unable to parse a StacHierarchy at $path")
        .toValidatedNel
    } orElse Opts
    .env[String]("API_STAC_HIERARCHY", help = stacHierarchyHelp)
    .mapValidated { path =>
      Validated
        .fromEither(parseHierarchyFromPath(path))
        .leftMap(_ => s"Unable to parse a StacHierarchy at $path")
        .toValidatedNel
    } withDefault (StacHierarchy.empty)

  val apiConfig: Opts[ApiConfig] = (
    externalPort,
    internalPort,
    apiHost,
    apiPath,
    apiScheme,
    defaultLimit,
    enableTransactions,
    stacHierarchy
  ) mapN ApiConfig.apply
}
