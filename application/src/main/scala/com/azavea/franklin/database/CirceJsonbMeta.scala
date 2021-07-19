package com.azavea.franklin.database

import cats.syntax.all._
import com.azavea.franklin.datamodel.MosaicDefinition
import com.azavea.stac4s.StacAsset
import com.azavea.stac4s.{StacCollection, StacItem}
import doobie._
import doobie.postgres.circe.jsonb.implicits._
import geotrellis.raster.histogram.Histogram
import io.circe._
import io.circe.syntax._

import scala.reflect.runtime.universe.TypeTag

object CirceJsonbMeta {

  def apply[Type: TypeTag: Encoder: Decoder] = {
    val get = Get[Json].temap(_.as[Type].leftMap { _.message })
    val put = Put[Json].tcontramap[Type](_.asJson)
    new Meta[Type](get, put)
  }
}

trait CirceJsonbMeta {
  implicit val stacItemMeta: Meta[StacItem]                 = CirceJsonbMeta[StacItem]
  implicit val stacCollectionMeta: Meta[StacCollection]     = CirceJsonbMeta[StacCollection]
  implicit val mosaicDefinitionMeta: Meta[MosaicDefinition] = CirceJsonbMeta[MosaicDefinition]
  implicit val stacAssetMeta: Meta[StacAsset]               = CirceJsonbMeta[StacAsset]
  implicit val histArrayMeta: Meta[Array[Histogram[Int]]]   = CirceJsonbMeta[Array[Histogram[Int]]]
}
