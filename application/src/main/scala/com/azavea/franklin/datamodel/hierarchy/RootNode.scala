package com.azavea.franklin.datamodel.hierarchy

import cats.syntax.option._
import com.azavea.franklin.datamodel._
import com.azavea.stac4s.StacLinkType
import com.azavea.stac4s.`application/json`

final case class RootNode(children: List[StacHierarchy], items: List[ItemPath])
    extends StacHierarchy {

  def childLink(apiHost: String): Link =
    throw new Exception("No child link should be constructed for RootNode instances")

  def _id: String = "__root__"

  val path: List[String] = List.empty[String]

  override def updatePaths(previousPaths: List[String] = List.empty[String]): StacHierarchy = {
    val newPath = List.empty[String]
    updatePath(
      newPath,
      children.map(_.updatePaths(newPath))
    )
  }

  def updatePath(newPath: List[String], newChildren: List[StacHierarchy]): StacHierarchy = {
    assert(newPath.isEmpty)
    this.copy(children = newChildren)
  }
}

object RootNode {

  def apply(children: List[StacHierarchy], items: List[ItemPath]): RootNode =
    new RootNode(children, items)
      .updatePaths()
      .asInstanceOf[RootNode]
}
