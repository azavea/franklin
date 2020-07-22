package com.azavea.franklin.extensions

import cats.Functor
import cats.implicits._
import com.azavea.franklin.extensions.validation.syntax._
import com.azavea.stac4s.extensions.eo.EOItemExtension
import com.azavea.stac4s.extensions.label.{LabelItemExtension, LabelLinkExtension}
import com.azavea.stac4s.extensions.layer.LayerItemExtension
import com.azavea.stac4s.{StacItem, StacLink, StacLinkType}
import eu.timepit.refined.auto._
import monocle.macros.GenLens

package object validation {

  def getItemValidator(extensions: List[String]): StacItem => StacItem = {
    makeValidator(extensions map { s => (item: StacItem) =>
      {
        ExtensionName.fromString(s) match {
          case Label        => item.validate[LabelItemExtension]("label")
          case Layer        => item.validate[LayerItemExtension]("layer")
          case EO           => item.validate[EOItemExtension]("eo")
          case Unchecked(_) => item
        }
      }
    })
  }

  def getLinkValidator(extensions: List[String]): StacLink => StacLink = {
    makeValidator(extensions map { s => (link: StacLink) =>
      ExtensionName.fromString(s) match {
        case Label        => link.validateWhen[LabelLinkExtension]("label", _.rel == StacLinkType.Source)
        case Layer        => link
        case EO           => link
        case Unchecked(_) => link
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
