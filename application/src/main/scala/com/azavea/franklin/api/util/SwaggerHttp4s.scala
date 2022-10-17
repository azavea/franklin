package com.azavea.franklin.api.util

import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.Location
import org.http4s.headers.`Content-Type`
import sttp.tapir.swagger.http4s._

import scala.concurrent.ExecutionContext

import java.util.Properties

/** Tapir exposes no way to adjust headers on these endpoints, so this is a slightly modified
  * version of some code provided in tapir 0.17.
  */
class SwaggerHttp4s(
    yaml: String,
    contextPath: String = "docs",
    yamlName: String = "docs.yaml",
    redirectQuery: Map[String, Seq[String]] = Map.empty
) {

  private val swaggerVersion = {
    val p = new Properties()
    val pomProperties =
      getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties")
    try p.load(pomProperties)
    finally pomProperties.close()
    p.getProperty("version")
  }

  def routes[F[_]: Effect: ContextShift: Sync]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    val mediaType: MediaType = new MediaType("application", "vnd.oai.openapi+json;version=3.0")
    val contentType          = `Content-Type`(mediaType)

    HttpRoutes.of[F] {
      case path @ GET -> Root / `contextPath` =>
        val queryParameters = Map("url" -> Seq(s"${path.uri}/$yamlName")) ++ redirectQuery
        Uri
          .fromString(s"${path.uri}/index.html")
          .map(uri => uri.setQueryParams(queryParameters))
          .map(uri => PermanentRedirect(Location(uri)))
          .getOrElse(NotFound())
      case GET -> Root / `contextPath` / `yamlName` =>
        Ok(yaml)
          .map(_.withContentType(contentType))
      case GET -> Root / `contextPath` / swaggerResource =>
        StaticFile
          .fromResource(
            s"/META-INF/resources/webjars/swagger-ui/$swaggerVersion/$swaggerResource",
            Blocker.liftExecutionContext(ExecutionContext.global)
          )
          .getOrElseF(NotFound())
    }
  }
}
