package com.azavea.franklin.error

import com.azavea.franklin.datamodel.MapCenter

sealed abstract class MosaicDefinitionError {
  val msg: String
}

final case class ItemMissingAsset(itemId: String, assetKey: String) extends MosaicDefinitionError {
  val msg = s"Item $itemId does not have an asset named $assetKey"
}

final case class ItemDoesNotExist(itemId: String, collectionId: String)
    extends MosaicDefinitionError {
  val msg = s"Item $itemId does not exist in collection $collectionId"
}
