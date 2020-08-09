package com.azavea.franklin.extensions.validation

import cats.syntax.semigroup._
import com.azavea.stac4s.extensions.{ItemExtension, LinkExtension}
import com.azavea.stac4s.syntax._
import com.azavea.stac4s.{StacItem, StacLink}
import eu.timepit.refined.types.string.NonEmptyString

package object syntax {

  implicit class validateItem(item: StacItem) {

    def validate[T: ItemExtension](name: NonEmptyString): StacItem = {
      val extensionResult     = item.getExtensionFields[T]
      val validationExtension = ValidationExtension.fromResult(extensionResult, name)
      val existingValidation  = item.getExtensionFields[ValidationExtension]
      item.addExtensionFields(existingValidation map { _ |+| validationExtension } getOrElse {
        validationExtension
      })
    }
  }

  implicit class validateLink(link: StacLink) {

    def validate[T: LinkExtension](name: NonEmptyString): StacLink = {
      val extensionResult     = link.getExtensionFields[T]
      val validationExtension = ValidationExtension.fromResult(extensionResult, name)
      val existingValidation  = link.getExtensionFields[ValidationExtension]
      link.addExtensionFields(existingValidation map { _ |+| validationExtension } getOrElse {
        validationExtension
      })
    }
  }

  implicit class validateLinkWhen(link: StacLink) {

    def validateWhen[T: LinkExtension](
        name: NonEmptyString,
        predicate: StacLink => Boolean
    ): StacLink = {
      if (predicate(link)) {
        link.validate(name)
      } else {
        link
      }
    }
  }
}
