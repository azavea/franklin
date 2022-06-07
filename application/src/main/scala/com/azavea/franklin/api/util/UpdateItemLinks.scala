package com.azavea.franklin.api.util

import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.endpoints.ItemEndpoints
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.database.PGStacQueries
import com.azavea.franklin.datamodel._
import com.azavea.stac4s._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.NonNegInt
import io.chrisdavenport.log4cats.Logger
import io.circe._

case class UpdateItemLinks(apiConfig: ApiConfig) {

  def createSelfLink(item: StacItem): StacLink =
    StacLink(
      s"${apiConfig.apiHost}/collections/${item.collection.get}/items/${item.id}",
      StacLinkType.Self,
      Some(`application/json`),
      None
    )

  def apply(item: StacItem): StacItem = {
    val prunedLinks = item.links.filter { link => link.rel != StacLinkType.Self }
    item.copy(links = prunedLinks :+ createSelfLink(item))
  }
}
