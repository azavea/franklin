package com.azavea.franklin.datamodel

import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.string.NonEmptyString

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed trait TileMatrixRequest {
  val z: Int
  val x: Int
  val y: Int
  val collection: String

  def urlDecode(rawString: String): String =
    URLDecoder.decode(rawString, StandardCharsets.UTF_8.toString)
}

case class ItemRasterTileRequest(
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
    lowerQuantileOption: Option[Quantile],
    singleBand: Option[NonNegInt]
) extends TileMatrixRequest {

  val collection = urlDecode(collectionRaw)
  val item       = urlDecode(itemRaw)

  val redBand   = redBandOption.getOrElse(0)
  val greenBand = greenBandOption.getOrElse(1)
  val blueBand  = blueBandOption.getOrElse(2)

  val bands = Seq(redBand, greenBand, blueBand)

  // Because lists are 0 indexed and humans are 1 indexed we need to adjust
  val upperQuantile = upperQuantileOption.map(_.value).getOrElse(100) - 1
  val lowerQuantile = lowerQuantileOption.map(_.value).getOrElse(-1) + 1

  val zxy = (z, x, y)

}

case class MapboxVectorTileFootprintRequest(
    collectionRaw: String,
    z: Int,
    x: Int,
    y: Int,
    colorField: NonEmptyString
) extends TileMatrixRequest {
  val collection = urlDecode(collectionRaw)
}
