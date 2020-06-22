package com.azavea.franklin.extensions

import com.azavea.stac4s.StacItem
import com.azavea.stac4s.extensions.label.LabelItemExtension
import com.azavea.stac4s.extensions.layer.LayerItemExtension
import com.azavea.stac4s.extensions.label.LabelLinkExtension
import com.azavea.stac4s.StacLink

import monocle.macros.GenLens

package object validation {

  def getItemValidator(extensions: List[ExtensionName]): StacItem => StacItem = {
    val checks = extensions map {
      case Label        => ItemValidator[LabelItemExtension].validate _
      case Layer        => ItemValidator[LayerItemExtension].validate _
      case Unchecked(_) => (x: StacItem) => x
    }

    checks reduce { _ compose _ }
  }

  def getLinkValidator(extensions: List[ExtensionName]): StacLink => StacLink = {
    val checks = extensions map {
      case Label        => LinkValidator[LabelLinkExtension].validate _
      case Layer        => (x: StacLink) => x
      case Unchecked(_) => (x: StacLink) => x
    }

    checks reduce { _ compose _ }
  }

  val linksLens = GenLens[StacItem](_.links)
}
