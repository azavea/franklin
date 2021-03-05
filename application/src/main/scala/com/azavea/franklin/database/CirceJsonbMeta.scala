package com.azavea.franklin.database

import cats.syntax.all._
import com.azavea.stac4s.StacLink
import com.azavea.stac4s.extensions.layer.StacLayerProperties
import com.azavea.stac4s.{StacCollection, StacItem}
import doobie._
import doobie.postgres.circe.jsonb.implicits._
import io.circe._
import io.circe.syntax._

import scala.reflect.runtime.universe.TypeTag
import com.azavea.stac4s.extensions.layer.StacLayer

object CirceJsonbMeta {

  def apply[Type: TypeTag: Encoder: Decoder] = {
    val get = Get[Json].temap(_.as[Type].leftMap { _.message })
    val put = Put[Json].tcontramap[Type](_.asJson)
    new Meta[Type](get, put)
  }
}

trait CirceJsonbMeta {
  implicit val stacItemMeta: Meta[StacItem]             = CirceJsonbMeta[StacItem]
  implicit val stacCollectionMeta: Meta[StacCollection] = CirceJsonbMeta[StacCollection]
  implicit val stacLayerMeta: Meta[StacLayer]           = CirceJsonbMeta[StacLayer]
}
