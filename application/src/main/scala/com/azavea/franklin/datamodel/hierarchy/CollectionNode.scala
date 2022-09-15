package com.azavea.franklin.datamodel.hierarchy

import cats.syntax.option._
import com.azavea.franklin.datamodel._
import com.azavea.stac4s.StacLinkType
import com.azavea.stac4s.`application/json`

final case class CollectionNode(
    collectionId: String
) extends StacHierarchy {
  val children: List[StacHierarchy] = List.empty[StacHierarchy]
  val path: List[String]            = List.empty[String]
  val items: List[ItemPath]         = List.empty[ItemPath]

  val _id = collectionId

  def childLink(apiHost: String): Link = Link(
    href = s"${apiHost}/collections/${collectionId}",
    rel = StacLinkType.Child,
    _type = `application/json`.some
  )

  def updatePath(newPath: List[String], newChildren: List[StacHierarchy]): StacHierarchy =
    this
}
