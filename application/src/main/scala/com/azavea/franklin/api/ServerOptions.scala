package com.azavea.franklin.api

import cats.effect.{ContextShift, Sync}
import io.circe.{CursorOp, DecodingFailure}
import sttp.tapir.DecodeResult
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.{DecodeFailureContext, ServerDefaults}

object ServerOptions {

  private def handleDecodingErr(err: Throwable): Option[String] = {
    err match {
      case DecodeResult.Error.JsonDecodeException(_, underlying) =>
        Some(
          underlying.getMessage()
        )
      case _ => None
    }
  }

  private def failureMessage(ctx: DecodeFailureContext): String = {
    ctx.failure match {
      case DecodeResult.Mismatch(expected, actual) => s"Expected: $expected. Received: $actual"
      case DecodeResult.Error(original, err)       => handleDecodingErr(err) getOrElse original
      case _                                       => ServerDefaults.FailureMessages.failureMessage(ctx)
    }
  }

  private val failureHandling =
    ServerDefaults.decodeFailureHandler.copy(failureMessage = failureMessage)

  /** Override the default decodeFailureHandler to produce nicer strings.
    *
    * Other overrideable values allow specifying how to produce the response message
    * and how to go from decoding failures in different parts of a request (headers,
    * query params, etc.) to a status code. For now, only the
    * DecodeFailureContext => String
    * has been overridden.
    */
  def defaultServerOptions[F[_]: Sync: ContextShift]: Http4sServerOptions[F] =
    Http4sServerOptions
      .default[F]
      .copy(
        decodeFailureHandler = failureHandling
      )
}
