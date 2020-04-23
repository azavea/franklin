package com.azavea.franklin.tile

import cats.data.{NonEmptyList => NEL}
import cats.syntax.either._
import geotrellis.proj4.CRS
import geotrellis.raster.TileLayout
import geotrellis.raster.io.geotiff.BandType
import geotrellis.raster.io.geotiff.GeoTiffOptions
import geotrellis.raster.io.geotiff.GeoTiffSegmentLayout
import geotrellis.raster.io.geotiff.NewSubfileType
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.compression.Compression
import geotrellis.raster.io.geotiff.compression.{Compression, Decompressor}
import geotrellis.raster.io.geotiff.reader.GeoTiffInfo
import geotrellis.raster.io.geotiff.tags.TiffTags
import geotrellis.raster.io.geotiff.tags.TiffTags
import geotrellis.util.StreamingByteReader
import geotrellis.vector.Extent
import geotrellis.vector.Extent
import geotrellis.vector.io.json.CrsFormats
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

import java.nio.ByteOrder
import java.nio.ByteOrder

/** Container for metadata for Tiffs
  *
  * This tracks closely to GeoTiffInfo, but omits and adds a few things
  * to make it easier to serialize. As such, this class has a static helper method
  * to construct itself from an instance of GeoTiffInfo and also transform back
  * into one
  *
  * @param extent
  * @param crs
  * @param tags
  * @param tiffTags
  * @param options
  * @param bandType
  * @param segmentLayout
  * @param compression
  * @param bandCount
  * @param noDataValue
  * @param overviews
  */
case class SerializableGeotiffInfo(
    extent: Extent,
    crs: CRS,
    tags: Tags,
    tiffTags: TiffTags,
    options: GeoTiffOptions,
    bandType: BandType,
    byteOrder: ByteOrder,
    segmentLayout: GeoTiffSegmentLayout,
    compression: Compression,
    bandCount: Int,
    noDataValue: Option[Double],
    overviews: List[SerializableGeotiffInfo] = Nil
) {

  def toGeotiffInfo(segmentReader: StreamingByteReader): GeoTiffInfo = {
    GeoTiffInfo(
      extent,
      crs,
      tags,
      options,
      bandType,
      LazySegmentBytes(segmentReader, tiffTags),
      Decompressor(tiffTags, byteOrder),
      segmentLayout,
      compression,
      bandCount,
      noDataValue,
      overviews.map { info =>
        GeoTiffInfo(
          info.extent,
          info.crs,
          info.tags,
          info.options,
          info.bandType,
          LazySegmentBytes(segmentReader, info.tiffTags),
          Decompressor(info.tiffTags, byteOrder),
          info.segmentLayout,
          info.compression,
          info.bandCount,
          info.noDataValue
        )
      }
    )
  }
}

object SerializableGeotiffInfo extends CrsFormats {

  // GT actually has a *two* decoders for tile layout, but because there are two
  // there is an ambiguous implicit resolution problem...
  implicit val tileLayoutDecoder: Decoder[TileLayout]         = deriveDecoder[TileLayout]
  implicit val geotiffSegmentLayoutDecoder                    = deriveDecoder[GeoTiffSegmentLayout]
  implicit val newSubFileTypeDecoder: Decoder[NewSubfileType] = deriveDecoder[NewSubfileType]
  implicit val geotiffOptionsDecoder: Decoder[GeoTiffOptions] = deriveDecoder[GeoTiffOptions]

  implicit val tileLayoutEncoder: Encoder[TileLayout]         = deriveEncoder[TileLayout]
  implicit val geotiffSegmentLayoutEncoder                    = deriveEncoder[GeoTiffSegmentLayout]
  implicit val newSubFileTypeEncoder: Encoder[NewSubfileType] = deriveEncoder[NewSubfileType]
  implicit val geotiffOptionsEncoder: Encoder[GeoTiffOptions] = deriveEncoder[GeoTiffOptions]

  implicit val byteOrderEncoder: Encoder[ByteOrder] = Encoder.encodeString.contramap(_.toString)

  implicit val byteOrderDecoder: Decoder[ByteOrder] =
    Decoder.decodeString.emap {
      case "BIG_ENDIAN"    => Either.right(ByteOrder.BIG_ENDIAN)
      case "LITTLE_ENDIAN" => Either.right(ByteOrder.LITTLE_ENDIAN)
      case s               => Either.left(s"Unknown Byte Order: $s")
    }

  implicit val bsgtEncoder: Encoder[SerializableGeotiffInfo] =
    deriveEncoder[SerializableGeotiffInfo]

  implicit val bsgtDecoder: Decoder[SerializableGeotiffInfo] =
    deriveDecoder[SerializableGeotiffInfo]

  def fromGeotiffInfo(info: GeoTiffInfo, tiffTags: NEL[TiffTags]): SerializableGeotiffInfo = {
    val byteOrder: ByteOrder = info.decompressor.byteOrder
    SerializableGeotiffInfo(
      info.extent,
      info.crs,
      info.tags,
      tiffTags.head,
      info.options,
      info.bandType,
      byteOrder,
      info.segmentLayout,
      info.compression,
      info.bandCount,
      info.noDataValue,
      info.overviews
        .zip(tiffTags.tail)
        .map {
          case (i, t) =>
            SerializableGeotiffInfo(
              i.extent,
              i.crs,
              i.tags,
              t,
              i.options,
              i.bandType,
              byteOrder,
              i.segmentLayout,
              i.compression,
              i.bandCount,
              i.noDataValue
            )
        }
    )
  }
}
