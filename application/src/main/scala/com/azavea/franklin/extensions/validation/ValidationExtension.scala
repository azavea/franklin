package com.azavea.franklin.extensions.validation

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import cats.kernel.Semigroup
import com.azavea.stac4s.extensions.CollectionExtension
import com.azavea.stac4s.extensions.StacAssetExtension
import com.azavea.stac4s.extensions.{
  ExtensionResult,
  IntervalExtension,
  ItemExtension,
  LinkExtension
}
import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.refined._
import io.circe.syntax._

final case class ValidationExtension(
    attemptedExtensions: NonEmptyList[NonEmptyString],
    errors: List[NonEmptyString]
)

object ValidationExtension {

  implicit val decValidationExtension: Decoder[ValidationExtension] = Decoder.forProduct2(
    "validation:attemptedExtensions",
    "validation:errors"
  )((extensions: NonEmptyList[NonEmptyString], errors: List[NonEmptyString]) =>
    ValidationExtension(extensions, errors)
  )

  implicit val encValidationExtension: Encoder.AsObject[ValidationExtension] = Encoder
    .AsObject[Map[String, Json]]
    .contramapObject((validationExtensionFields: ValidationExtension) =>
      Map(
        "validation:attemptedExtensions" -> validationExtensionFields.attemptedExtensions.asJson,
        "validation:errors"              -> validationExtensionFields.errors.asJson
      )
    )

  implicit val validationExtensionItemExtension: ItemExtension[ValidationExtension] =
    ItemExtension.instance

  implicit val validationExtensionLinkExtension: LinkExtension[ValidationExtension] =
    LinkExtension.instance

  implicit val validationExtensionAssetExtension: StacAssetExtension[ValidationExtension] =
    StacAssetExtension.instance

  implicit val validationExtensionCollectionExtension: CollectionExtension[ValidationExtension] =
    CollectionExtension.instance

  implicit val validationExtensionIntervalExtension: IntervalExtension[ValidationExtension] =
    IntervalExtension.instance

  implicit val semigroupValidationExtension: Semigroup[ValidationExtension] =
    new Semigroup[ValidationExtension] {

      def combine(x: ValidationExtension, y: ValidationExtension): ValidationExtension = {
        ValidationExtension(
          x.attemptedExtensions.concat(y.attemptedExtensions.toList),
          x.errors ++ y.errors
        )
      }
    }

  def success(name: NonEmptyString) = ValidationExtension(
    NonEmptyList.of(name),
    Nil
  )

  def failure(name: NonEmptyString, errors: List[DecodingFailure]) =
    ValidationExtension(NonEmptyList.of(name), errors map { (err: DecodingFailure) =>
      NonEmptyString.from(CursorOp.opsToPath(err.history))
    } collect {
      case Right(v) => v
    })

  def fromResult[T](result: ExtensionResult[T], name: NonEmptyString) = result match {
    case Invalid(errs) =>
      failure(name, errs collect {
        case e: DecodingFailure => e
      })
    case Valid(_) =>
      success(name)
  }
}
