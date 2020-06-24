package com.azavea.franklin.extensions

import cats.Functor
import cats.implicits._
import com.azavea.stac4s.extensions.label.{LabelItemExtension, LabelLinkExtension}
import com.azavea.stac4s.extensions.layer.LayerItemExtension
import com.azavea.stac4s.{StacItem, StacLink}
import monocle.macros.GenLens

package object validation {

  def getItemValidator(extensions: List[String]): StacItem => StacItem = {
    makeValidator(extensions map { s =>
      ExtensionName.fromString(s) match {
        case Label        => ItemValidator[LabelItemExtension].validate _
        case Layer        => ItemValidator[LayerItemExtension].validate _
        case Unchecked(_) => (x: StacItem) => x
      }
    })
  }

  def getLinkValidator(extensions: List[String]): StacLink => StacLink = {
    makeValidator(extensions map { s =>
      ExtensionName.fromString(s) match {
        case Label        => LinkValidator[LabelLinkExtension].validate _
        case Layer        => (x: StacLink) => x
        case Unchecked(_) => (x: StacLink) => x
      }
    })
  }

  private def makeValidator[A](funcs: List[A => A]): A => A =
    funcs.toNel map { _.reduce((f1: A => A, f2: A => A) => f1 compose f2) } getOrElse { x => x }

  val linksLens = GenLens[StacItem](_.links)

  def validateItemAndLinks(item: StacItem): StacItem = {
    val itemValidator = getItemValidator(item.stacExtensions)
    val linkValidator = getLinkValidator(item.stacExtensions)

    (linksLens.modify(Functor[List].lift(linkValidator)) compose itemValidator)(item)
  }
}
