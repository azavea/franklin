package com.azavea.franklin.api.endpoints

import cats.effect.Concurrent
import com.azavea.franklin.datamodel.{
  ItemRasterTileRequest,
  MapboxVectorTileFootprintRequest,
  Quantile,
  TileJson
}
import com.azavea.franklin.error.NotFound
import com.azavea.stac4s.extensions.layer
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.Json
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode.{NotFound => NF}
import sttp.tapir._
import sttp.tapir.codec.refined._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

class TileEndpoints[F[_]: Concurrent](enableLayers: Boolean) {
  val basePath = "layers"

  val listLayers = basePath.out(jsonBody[List[Layer]])
}
