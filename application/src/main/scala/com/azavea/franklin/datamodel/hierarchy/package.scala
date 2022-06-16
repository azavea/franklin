package com.azavea.franklin.datamodel

import cats.syntax.functor._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._


package object hierarchy {
  implicit def stacHierarchyEncoder: Encoder[StacHierarchy] =
    Encoder.instance {
      case root @ RootNode(_, _) =>
        root.asJson
      case catalog @ CollectionNode(_) =>
        catalog.asJson
      case collection @ CatalogNode(_, _, _, _, _, _) =>
        collection.asJson
    }
  implicit def stacHierarchyDecoder: Decoder[StacHierarchy] =
    List[Decoder[StacHierarchy]](
      Decoder[CatalogNode].widen,
      Decoder[CollectionNode].widen,
      Decoder[RootNode].widen
    ).reduceLeft(_ or _)

  implicit lazy val itemPathEncoder: Encoder[ItemPath] = deriveEncoder
  implicit lazy val itemPathDecoder: Decoder[ItemPath] = deriveDecoder

  implicit lazy val rootNodeEncoder: Encoder[RootNode] = deriveEncoder
  implicit lazy val rootNodeDecoder: Decoder[RootNode] = deriveDecoder

  implicit lazy val collectionNodeEncoder: Encoder[CollectionNode] = deriveEncoder
  implicit lazy val collectionNodeDecoder: Decoder[CollectionNode] = deriveDecoder

  implicit lazy val catalogNodeEncoder: Encoder[CatalogNode] = deriveEncoder
  implicit lazy val catalogNodeDecoder: Decoder[CatalogNode] = deriveDecoder

}
