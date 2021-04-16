package com.azavea.franklin.extensions.validation

import cats.syntax.semigroup._
import com.azavea.stac4s.extensions.{IntervalExtension, ItemExtension, LinkExtension}
import com.azavea.stac4s.syntax._
import com.azavea.stac4s.{StacItem, StacLink}
import eu.timepit.refined.types.string.NonEmptyString
import com.azavea.stac4s.Interval

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

  implicit class validateInterval(interval: Interval) {

    def validate[T: IntervalExtension](name: NonEmptyString): Interval = {
      val extensionResult     = interval.getExtensionFields[T]
      val validationExtension = ValidationExtension.fromResult(extensionResult, name)
      val existingValidation  = interval.getExtensionFields[ValidationExtension]
      interval.addExtensionFields(existingValidation map { _ |+| validationExtension } getOrElse {
        validationExtension
      })
    }
  }

}
