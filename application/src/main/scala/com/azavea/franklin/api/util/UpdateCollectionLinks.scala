package com.azavea.franklin.api.util

import cats.syntax.option._
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.datamodel.{Collection, Link}
import com.azavea.stac4s._
import org.http4s.Method

case class UpdateCollectionLinks(apiConfig: ApiConfig) {

  def createSelfLink(collection: Collection): Link = {
    Link(
      s"${apiConfig.apiHost}/collections/${collection.id}",
      StacLinkType.Self,
      `application/json`.some,
      None,
      Method.GET.some
    )
  }

  def createItemsLink(collection: Collection): Link = Link(
    s"${apiConfig.apiHost}/collections/${collection.id}/items",
    StacLinkType.Items,
    `application/json`.some,
    None,
    Method.GET.some
  )

  def constructRootLink: Link =
    Link(
      apiConfig.apiHost,
      StacLinkType.StacRoot,
      Some(`application/json`),
      None,
      Some(Method.GET)
    )

  def apply(collection: Collection): Collection = {
    val newLinks = List(createSelfLink(collection), createItemsLink(collection), constructRootLink)
    collection.copy(links = collection.links ++ newLinks)
  }
}
