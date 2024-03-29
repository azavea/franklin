package com.azavea.franklin.api.middleware

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.functor._
import io.chrisdavenport.log4cats.Logger
import io.circe.syntax._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{HttpRoutes, Request}

import java.time.Instant

class AccessLoggingMiddleware[F[_]: Sync](
    service: HttpRoutes[F],
    logger: Logger[F]
) {

  def withLogging(enabled: Boolean) = {
    if (!enabled) service
    else {
      Kleisli { (request: Request[F]) =>
        val requestStart = Instant.now
        val headerWhitelist: Set[CaseInsensitiveString] =
          Set(
            CaseInsensitiveString("user-agent"),
            CaseInsensitiveString("accept-encoding"),
            CaseInsensitiveString("referer"),
            CaseInsensitiveString("origin"),
            CaseInsensitiveString("X-Amzn-Trace-Id")
          )
        val headers =
          Map(
            request.headers.toList.filter(header => headerWhitelist.contains(header.name)) map {
              header => header.name.toString.toLowerCase -> header.value.asJson
            }: _*
          )
        val requestData =
          Map(
            "method"       -> request.method.toString.asJson,
            "uri"          -> s"${request.uri}".asJson,
            "httpVersion"  -> s"${request.httpVersion}".asJson,
            "requesterIp"  -> (request.remote map { _.toString }).asJson,
            "forwardedFor" -> (request.from map { _.toString }).asJson
          ) ++ headers
        service.run(request) flatMap { resp =>
          val requestEnd = Instant.now
          val duration   = (requestEnd.toEpochMilli - requestStart.toEpochMilli)
          val responseData = Map(
            "durationInMillis" -> duration.asJson,
            "statusCode"       -> resp.status.code.asJson
          )
          OptionT.liftF {
            logger.info((requestData ++ responseData).asJson.noSpaces) map { _ => resp }
          }
        }
      }
    }
  }
}
