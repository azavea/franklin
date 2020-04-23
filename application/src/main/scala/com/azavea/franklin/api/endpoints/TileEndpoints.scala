package com.azavea.franklin.api.endpoints

import com.azavea.franklin.datamodel.{
  ItemRasterTileRequest,
  MapboxVectorTileFootprintRequest,
  Quantile
}
import com.azavea.franklin.error.NotFound
import sttp.model.StatusCode.{NotFound => NF}
import sttp.tapir._
import sttp.tapir.codec.refined._
import sttp.tapir.json.circe._

class TileEndpoints(enableTiles: Boolean) {

  val basePath = "tiles" / "collections"
  val zxyPath  = path[Int] / path[Int] / path[Int]

  val itemRasterTilePath: EndpointInput[(String, String, Int, Int, Int)] =
    (basePath / path[String] / "items" / path[String] / "WebMercatorQuad" / zxyPath)

  val collectionFootprintTilePath: EndpointInput[(String, Int, Int, Int)] =
    (basePath / path[String] / "footprint" / "WebMercatorQuad" / zxyPath)

  val itemRasterTileParameters: EndpointInput[ItemRasterTileRequest] =
    itemRasterTilePath
      .and(query[String]("asset"))
      .and(query[Option[Int]]("redBand"))
      .and(query[Option[Int]]("greenBand"))
      .and(query[Option[Int]]("blueBand"))
      .and(query[Option[Quantile]]("upperQuantile"))
      .and(query[Option[Quantile]]("lowerQuantile"))
      .mapTo(ItemRasterTileRequest)

  val itemRasterTileEndpoint: Endpoint[ItemRasterTileRequest, NotFound, Array[Byte], Nothing] =
    endpoint.get
      .in(itemRasterTileParameters)
      .out(binaryBody[Array[Byte]])
      .out(header("content-type", "image/png"))
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("Raster Tile endpoint for Collection Item")
      .name("collectionItemTiles")

  val collectionFootprintTileEndpoint
      : Endpoint[MapboxVectorTileFootprintRequest, NotFound, Array[Byte], Nothing] =
    endpoint.get
      .in(collectionFootprintTilePath.mapTo(MapboxVectorTileFootprintRequest))
      .out(binaryBody[Array[Byte]])
      .out(header("content-type", "application/vnd.mapbox-vector-tile"))
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("MVT endpoint for a collection's footprint")
      .name("collectionFootprintTiles")

  val endpoints = enableTiles match {
    case true => List(itemRasterTileEndpoint, collectionFootprintTileEndpoint)
    case _    => List.empty
  }
}
