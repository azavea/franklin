package com.azavea.franklin.api.endpoints

import com.azavea.franklin.error.NotFound
import sttp.model.StatusCode.{NotFound => NF}
import sttp.tapir._
import sttp.tapir.codec.refined._
import sttp.tapir.json.circe._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

case class RasterTileRequest(
    collectionRaw: String,
    itemRaw: String,
    z: Int,
    x: Int,
    y: Int,
    asset: String,
    redBandOption: Option[Int],
    greenBandOption: Option[Int],
    blueBandOption: Option[Int],
    upperQuantileOption: Option[Quantile],
    lowerQuantileOption: Option[Quantile]
) {

  val collection = URLDecoder.decode(collectionRaw, StandardCharsets.UTF_8.toString)
  val item       = URLDecoder.decode(itemRaw, StandardCharsets.UTF_8.toString)

  val redBand   = redBandOption.getOrElse(0)
  val greenBand = greenBandOption.getOrElse(1)
  val blueBand  = blueBandOption.getOrElse(2)

  val bands = Seq(redBand, greenBand, blueBand)

  // Because lists are 0 indexed and humans are 1 indexed we need to adjust
  val upperQuantile = upperQuantileOption.map(_.value).getOrElse(100) - 1
  val lowerQuantile = lowerQuantileOption.map(_.value).getOrElse(-1) + 1

  val zxy = (z, x, y)

}

class TileEndpoints(enableTiles: Boolean) {

  val basePath = "tiles" / "collections"
  val zxyPath  = path[Int] / path[Int] / path[Int]

  val tilePath: EndpointInput[(String, String, Int, Int, Int)] =
    (basePath / path[String] / "items" / path[String] / "WebMercatorQuad" / zxyPath)

  val tileParameters: EndpointInput[RasterTileRequest] =
    tilePath
      .and(query[String]("asset"))
      .and(query[Option[Int]]("redBand"))
      .and(query[Option[Int]]("greenBand"))
      .and(query[Option[Int]]("blueBand"))
      .and(query[Option[Quantile]]("upperQuantile"))
      .and(query[Option[Quantile]]("lowerQuantile"))
      .mapTo(RasterTileRequest)

  val tileEndpoint: Endpoint[RasterTileRequest, NotFound, Array[Byte], Nothing] =
    endpoint.get
      .in(tileParameters)
      .out(binaryBody[Array[Byte]])
      .out(header("content-type", "image/png"))
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("Raster Tile endpoint for Collection Item")
      .name("collectionItemTiles")

  val endpoints = enableTiles match {
    case true => List(tileEndpoint)
    case _    => List.empty
  }
}
