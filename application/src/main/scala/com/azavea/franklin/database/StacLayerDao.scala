package com.azavea.franklin.database

import cats.data.{EitherT, OptionT}
import cats.syntax.all._
import com.azavea.franklin.database
import com.azavea.franklin.datamodel.{Context, PaginationToken, SearchMethod, StacSearchCollection}
import com.azavea.franklin.extensions.paging.PagingLinkExtension
import com.azavea.stac4s._
import com.azavea.stac4s.syntax._
import doobie.Fragment
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.refined.implicits._
import doobie.util.update.Update
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString
import geotrellis.vector.{Geometry, Projected}
import io.circe.syntax._
import io.circe.{DecodingFailure, Json}

import java.time.Instant
import com.azavea.stac4s.extensions.layer.StacLayer
import doobie.util.Get
import com.azavea.stac4s.extensions.layer.StacLayerProperties

object StacLayerDao extends Dao[StacLayer] {
  val tableName = "layers"

  val selectF = fr"SELECT id, extent, geom, properties, links"
}
