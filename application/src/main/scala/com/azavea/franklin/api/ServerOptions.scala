package com.azavea.franklin.api

import io.circe.{CursorOp, DecodingFailure}
import sttp.tapir.DecodeResult
import sttp.tapir.server.{DecodeFailureContext, ServerDefaults}
import sttp.tapir.server.http4s.Http4sServerOptions
import cats.effect.{ContextShift, Sync}

object ServerOptions {

  private def handleDecodingErr(err: Throwable): Option[String] = err match {
    case DecodingFailure("Attempt to decode value on failed cursor", history) =>
      Some(
        s"I expected to find a value at ${CursorOp.opsToPath(history)}, but there was nothing"
      )
    case DecodingFailure(s, history) =>
      Some(
        s"I found something unexpected at ${CursorOp.opsToPath(history)}. I expected a value of type $s"
      )
    case _ => None
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
