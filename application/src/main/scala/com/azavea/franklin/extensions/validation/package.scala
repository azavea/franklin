package com.azavea.franklin.extensions

import cats.implicits._
import com.azavea.stac4s.{StacItem, StacLink}
import com.azavea.stac4s.extensions.label.{LabelItemExtension, LabelLinkExtension}
import com.azavea.stac4s.extensions.layer.LayerItemExtension

import monocle.macros.GenLens

package object validation {

  def getItemValidator(extensions: List[ExtensionName]): StacItem => StacItem = {
    makeValidator(extensions map {
      case Label        => ItemValidator[LabelItemExtension].validate _
      case Layer        => ItemValidator[LayerItemExtension].validate _
      case Unchecked(_) => (x: StacItem) => x
    })
  }

  def getLinkValidator(extensions: List[ExtensionName]): StacLink => StacLink = {
    makeValidator(extensions map {
      case Label        => LinkValidator[LabelLinkExtension].validate _
      case Layer        => (x: StacLink) => x
      case Unchecked(_) => (x: StacLink) => x
    })
  }

  private def makeValidator[A](funcs: List[A => A]): A => A =
    funcs.toNel map { _.reduce((f1: A => A, f2: A => A) => f1 compose f2) } getOrElse { x => x }

  val linksLens = GenLens[StacItem](_.links)
}
