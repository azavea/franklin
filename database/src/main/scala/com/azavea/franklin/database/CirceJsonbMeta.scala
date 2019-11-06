package com.azavea.franklin.database

import doobie._
import doobie.postgres.circe.jsonb.implicits._
import io.circe._
import io.circe.syntax._
import cats.implicits._
import geotrellis.server.stac.StacItem

import scala.reflect.runtime.universe.TypeTag
import geotrellis.server.stac.StacCollection

object CirceJsonbMeta {

  def apply[Type: TypeTag: Encoder: Decoder] = {
    val get = Get[Json].tmap[Type](_.as[Type].valueOr(throw _))
    val put = Put[Json].tcontramap[Type](_.asJson)
    new Meta[Type](get, put)
  }
}

trait CirceJsonbMeta {
  implicit val stacItemMeta: Meta[StacItem]             = CirceJsonbMeta[StacItem]
  implicit val stacCollectionMeta: Meta[StacCollection] = CirceJsonbMeta[StacCollection]
}
