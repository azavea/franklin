package com.azavea.franklin.extensions

import cats.Functor
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.all._
import com.azavea.franklin.extensions.validation.syntax._
import com.azavea.stac4s.StacCollection
import com.azavea.stac4s.extensions.eo.EOItemExtension
import com.azavea.stac4s.extensions.label.{LabelItemExtension, LabelLinkExtension}
import com.azavea.stac4s.extensions.layer.LayerItemExtension
import com.azavea.stac4s.syntax._
import com.azavea.stac4s.{StacItem, StacLink, StacLinkType}
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import io.chrisdavenport.log4cats.Logger
import io.circe.Encoder
import io.circe.Json
import io.circe.schema.Schema
import io.circe.syntax._
import monocle.macros.GenLens
import sttp.client._
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client.circe._
import sttp.model.{Uri => SttpUri}

import scala.util.Try

package object validation {

  val linksLens = GenLens[StacItem](_.links)

  type ExtensionRef[F[_], T] = Ref[F, Map[String, T => T]]

  private val itemLabelValidator = (item: StacItem) => item.validate[LabelItemExtension]("label")

  private val linkLabelValidator = (link: StacLink) =>
    link.validateWhen[LabelLinkExtension]("label", _.rel == StacLinkType.Source)

  private val labelItemLinkValidator =
    linksLens.modify(Functor[List].lift(linkLabelValidator)) compose itemLabelValidator

  private def getExtensionsRef[F[_]: Sync, T]: F[ExtensionRef[F, T]] =
    Ref.of(Map.empty)

  private def getExtensionsRef[F[_]: Sync, T](
      m: Map[String, T => T]
  ): F[ExtensionRef[F, T]] =
    Ref.of(m)

  private val knownItemExtensions: Map[String, StacItem => StacItem] = Map(
    "label"                                                                                  -> labelItemLinkValidator,
    "https://raw.githubusercontent.com/stac-extensions/label/v1.0.0/json-schema/schema.json" -> labelItemLinkValidator,
    "eo"                                                                                     -> ((item: StacItem) => item.validate[EOItemExtension]("eo")),
    "https://raw.githubusercontent.com/stac-extensions/eo/v1.0.0/json-schema/schema.json" -> (
      (item: StacItem) => item.validate[EOItemExtension]("eo")
    )
  )

  def itemExtensionsRef[F[_]: Sync]       = getExtensionsRef[F, StacItem](knownItemExtensions)
  def collectionExtensionsRef[F[_]: Sync] = getExtensionsRef[F, StacCollection]

  private def readAsSchema[F[_]: Sync](
      extension: String
  )(
      implicit backend: SttpBackend[F, Nothing, NothingT]
  ): F[Either[String, Schema]] = {
    val extensionUriParsed = SttpUri.parse(extension)
    extensionUriParsed flatTraverse { extensionUri =>
      basicRequest.get(extensionUri).response(asJson[Json]).send[F] map { resp =>
        resp.body
          .bimap(
            {
              case DeserializationError(_, _) =>
                s"Value at $extensionUri was not valid json and can't be used for extension schema validation"
              case HttpError(_, code) =>
                s"Could not read from $extensionUri while attempting to validate extensions. Response code was: $code"
              case _ =>
                s"Encountered an unknown error trying to read $extensionUri while validating extensions"
            },
            js => {
              Try(Schema.load(js))
            }
          )
          .flatMap(
            Either
              .fromTry(_)
              .leftMap(_ => s"""| Failed to load a schema from the json at $extensionUri.
                    | This can commonly be caused by links that can't be resolved during load.
                    | Not validating the extension at $extensionUri.""".trim.stripMargin)
          )
      }
    }
  }

  private val itemSchemaValidator = (schema: Schema) =>
    (name: NonEmptyString) =>
      (item: StacItem) => {
        val validationResult = schema.validate(item.asJson)
        validationResult.fold(
          errs =>
            item.addExtensionFields(ValidationExtension(NonEmptyList.of(name), (errs map { err =>
              NonEmptyString.unsafeFrom(err.location)
            }).toList)),
          _ => item.addExtensionFields(ValidationExtension(NonEmptyList.of(name), Nil))
        )
      }

  private val collectionSchemaValidator = (schema: Schema) =>
    (name: NonEmptyString) =>
      (collection: StacCollection) => {
        val validationResult = schema.validate(collection.asJson)
        validationResult.fold(
          errs =>
            collection.addExtensionFields(ValidationExtension(NonEmptyList.of(name), (errs map {
              err => NonEmptyString.unsafeFrom(err.location)
            }).toList)),
          _ => collection.addExtensionFields(ValidationExtension(NonEmptyList.of(name), Nil))
        )
      }

  private def fetchValidator[F[_]: Sync, T: Encoder](
      url: NonEmptyString,
      validator: Schema => NonEmptyString => T => T
  )(
      implicit backend: SttpBackend[F, Nothing, NothingT],
      logger: Logger[F]
  ): F[T => T] = {
    readAsSchema(url) flatMap { schemaResult =>
      schemaResult.fold(
        errMessage => logger.error(errMessage) *> Sync[F].pure(identity[T]),
        schema => Sync[F].pure((value: T) => validator(schema)(url)(value))
      )
    }
  }

  private def fetchItemValidator[F[_]: Sync](
      url: NonEmptyString
  )(
      implicit backend: SttpBackend[F, Nothing, NothingT],
      logger: Logger[F]
  ): F[StacItem => StacItem] = fetchValidator[F, StacItem](url, itemSchemaValidator)

  private def fetchCollectionValidator[F[_]: Sync](
      url: NonEmptyString
  )(
      implicit backend: SttpBackend[F, Nothing, NothingT],
      logger: Logger[F]
  ): F[StacCollection => StacCollection] =
    fetchValidator[F, StacCollection](url, collectionSchemaValidator)

  private def resolveItemValidator[F[_]: Sync](
      extension: String,
      extensionRef: ExtensionRef[F, StacItem]
  )(
      implicit backend: SttpBackend[F, Nothing, NothingT],
      logger: Logger[F]
  ): F[StacItem => StacItem] = {
    extensionRef.get flatMap { resolvedExtensions =>
      val existingO = resolvedExtensions.get(extension)
      existingO.fold({
        val nonEmptyUrl = NonEmptyString.from(extension)
        nonEmptyUrl.fold(
          _ =>
            logger
              .error("Can't build an extension validator from an empty extension url or name") *>
              Sync[F].pure(identity[StacItem] _),
          nonEmpty =>
            for {
              _         <- logger.debug(s"Validator for $nonEmpty was not cached. Fetching from source.")
              validator <- fetchItemValidator[F](nonEmpty)
              _         <- extensionRef.set(resolvedExtensions + (extension -> validator))
            } yield validator
        )
      })(
        Sync[F].pure(_)
      )
    }
  }

  private def resolveCollectionValidator[F[_]: Sync](
      extension: String,
      extensionRef: ExtensionRef[F, StacCollection]
  )(
      implicit backend: SttpBackend[F, Nothing, NothingT],
      logger: Logger[F]
  ): F[StacCollection => StacCollection] = {
    extensionRef.get flatMap { resolvedExtensions =>
      val existingO = resolvedExtensions.get(extension)
      existingO.fold({
        val nonEmptyUrl = NonEmptyString.from(extension)
        nonEmptyUrl.fold(
          _ =>
            logger
              .error("Can't build an extension validator from an empty extension url or name") *>
              Sync[F].pure(identity[StacCollection] _),
          nonEmpty =>
            for {
              validator <- fetchCollectionValidator[F](nonEmpty)
              _         <- extensionRef.set(resolvedExtensions + (extension -> validator))
            } yield validator
        )
      })(
        Sync[F].pure(_)
      )
    }
  }

  def makeCollectionValidator[F[_]: Sync](
      extensions: List[String],
      extensionRef: ExtensionRef[F, StacCollection]
  )(
      implicit backend: SttpBackend[F, Nothing, NothingT],
      logger: Logger[F]
  ): F[StacCollection => StacCollection] = {
    extensions traverse { extensionString =>
      resolveCollectionValidator(extensionString, extensionRef)
    } map { validators => validators.foldLeft(identity[StacCollection] _)(_ `compose` _) }
  }

  def makeItemValidator[F[_]: Sync](
      extensions: List[String],
      extensionRef: ExtensionRef[F, StacItem]
  )(
      implicit backend: SttpBackend[F, Nothing, NothingT],
      logger: Logger[F]
  ): F[StacItem => StacItem] = {
    extensions traverse { extensionString =>
      resolveItemValidator(extensionString, extensionRef)
    } map { validators => validators.foldLeft(identity[StacItem] _)(_ `compose` _) }
  }
}
