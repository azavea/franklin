package com.azavea.franklin.api.util

import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.datamodel.{Collection, Link}

import cats.syntax.option._
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

  def constructRootLink: Link =
    Link(
      apiConfig.apiHost.value,
      StacLinkType.StacRoot,
      Some(`application/json`),
      None,
      Some(Method.GET)
    )

  def apply(collection: Collection): Collection =
    collection.copy(links = collection.links ++ List(createSelfLink(collection), constructRootLink))
}
