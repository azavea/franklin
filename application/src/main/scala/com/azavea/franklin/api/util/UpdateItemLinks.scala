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
import io.chrisdavenport.log4cats.Logger
import io.circe._
import org.http4s.Method

case class UpdateItemLinks(apiConfig: ApiConfig) {

  def updateLinkHrefs(link: StacLink): StacLink = {
    val href =
      if (!link.href.startsWith("http") && link.href.endsWith(".json"))
        s"${apiConfig.apiHost}/${link.href.dropRight(5)}"
      else
        link.href
    link.copy(href = href)
  }

  def createSelfLink(item: StacItem): StacLink = {
    val collection =
      item.collection.getOrElse(throw new IllegalStateException("Item must have collection!"))
    StacLink(
      s"${apiConfig.apiHost}/collections/${collection}/items/${item.id}",
      StacLinkType.Self,
      Some(`application/json`),
      None
    )
  }

  def constructRootLink: StacLink = {
    StacLink(
      apiConfig.apiHost,
      StacLinkType.StacRoot,
      Some(`application/json`),
      None
    )
  }

  def apply(item: StacItem): StacItem = {
    val prunedLinks = item.links
      .filter { link => link.rel != StacLinkType.Self }
      .map(updateLinkHrefs)
    item.copy(links = prunedLinks ++ List(createSelfLink(item), constructRootLink))
  }
}
