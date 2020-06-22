package com.azavea.franklin.extensions.validation

import com.azavea.stac4s.StacLink
import com.azavea.stac4s.StacLinkType
import com.azavea.stac4s.extensions.LinkExtension
import com.azavea.stac4s.extensions.label.LabelLinkExtension
import com.azavea.stac4s.syntax._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString

trait LinkValidator[T] {
  def validate(link: StacLink): StacLink
}

object LinkValidator {

  def apply[T](implicit ev: LinkValidator[T]) = ev

  def identity[T] = new LinkValidator[T] {
    def validate(link: StacLink): StacLink = link
  }

  def instance[T: LinkExtension](name: NonEmptyString) = new LinkValidator[T] {

    def validate(link: StacLink) = {
      val extensionResult     = link.getExtensionFields[T]
      val validationExtension = ValidationExtension.fromResult(extensionResult, name)
      link.addExtensionFields(validationExtension)
    }
  }

  // It will often be the case that links only need to be checked for an extension
  // under certain conditions. For example, the label extension only modifies the
  // link with the relation "source". As long as the information required to make
  // this decision is local to the link, it can be encoded in this simple way.
  def when[T: LinkExtension](
      predicate: StacLink => Boolean,
      name: NonEmptyString
  ): LinkValidator[T] = new LinkValidator[T] {

    def validate(link: StacLink) = {
      if (predicate(link)) {
        instance(name).validate(link)
      } else {
        identity[T].validate(link)
      }
    }
  }

  implicit val labelLinkValidator: LinkValidator[LabelLinkExtension] =
    when(_.rel == StacLinkType.Source, "label")

}
