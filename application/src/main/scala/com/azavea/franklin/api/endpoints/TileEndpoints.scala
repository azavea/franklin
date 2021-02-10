package com.azavea.franklin.api.endpoints

import com.azavea.franklin.datamodel.{
  ItemRasterTileRequest,
  MapboxVectorTileFootprintRequest,
  Quantile
}
import com.azavea.franklin.error.NotFound
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Json
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

  val collectionFootprintTileParameters
      : EndpointInput[(String, Int, Int, Int, List[NonEmptyString])] =
    collectionFootprintTilePath.and(query[List[NonEmptyString]]("withField"))

  val collectionFootprintTileJsonPath: EndpointInput[String] =
    (basePath / path[String] / "footprint" / "tile-json")

  val itemRasterTileParameters: EndpointInput[ItemRasterTileRequest] =
    itemRasterTilePath
      .and(query[String]("asset"))
      .and(query[Option[Int]]("redBand"))
      .and(query[Option[Int]]("greenBand"))
      .and(query[Option[Int]]("blueBand"))
      .and(query[Option[Quantile]]("upperQuantile"))
      .and(query[Option[Quantile]]("lowerQuantile"))
      .and(query[Option[NonNegInt]]("singleBand"))
      .mapTo(ItemRasterTileRequest)

  val itemRasterTileEndpoint: Endpoint[ItemRasterTileRequest, NotFound, Array[Byte], Nothing] =
    endpoint.get
      .in(itemRasterTileParameters)
      .out(rawBinaryBody[Array[Byte]])
      .out(header("content-type", "image/png"))
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("Raster Tile endpoint for Collection Item")
      .name("collectionItemTiles")

  val collectionFootprintTileEndpoint
      : Endpoint[MapboxVectorTileFootprintRequest, NotFound, Array[Byte], Nothing] =
    endpoint.get
      .in(collectionFootprintTileParameters.mapTo(MapboxVectorTileFootprintRequest))
      .out(rawBinaryBody[Array[Byte]])
      .out(header("content-type", "application/vnd.mapbox-vector-tile"))
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("MVT endpoint for a collection's footprint")
      .name("collectionFootprintTiles")

  val collectionFootprintTileJson: Endpoint[String, NotFound, Json, Nothing] =
    endpoint.get
      .in(collectionFootprintTileJsonPath)
      .out(jsonBody[Json])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .description("TileJSON representation of this collection's footprint tiles")
      .name("collectionFootprintTileJSON")

  val endpoints = enableTiles match {
    case true =>
      List(itemRasterTileEndpoint, collectionFootprintTileEndpoint, collectionFootprintTileJson)
    case _ => List.empty
  }
}
