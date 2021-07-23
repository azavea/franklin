package com.azavea.franklin.error

import com.azavea.franklin.datamodel.ItemAsset
import com.azavea.franklin.datamodel.MapCenter

sealed abstract class MosaicDefinitionError {
  val msg: String
}

final case class ItemsMissingAsset(itemAssets: List[ItemAsset]) extends MosaicDefinitionError {

  private val itemAssetList =
    (itemAssets map { ia => s"(${ia.itemId}, ${ia.assetName})" }).mkString(", ")
  val msg = s"""Some items don't have the requested assets: $itemAssetList"""
}

final case class ItemsDoNotExist(itemIds: List[String], collectionId: String)
    extends MosaicDefinitionError {
  private val itemList = itemIds.mkString(", ")
  val msg              = s"Some items do not exist in collection $collectionId: $itemList"
}
