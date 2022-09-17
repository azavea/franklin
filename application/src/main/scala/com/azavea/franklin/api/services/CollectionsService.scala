package com.azavea.franklin.api.services

import cats.data.EitherT
import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.all._
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.api.endpoints.CollectionEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.MosaicDefinitionDao
import com.azavea.franklin.database.StacCollectionDao
import com.azavea.franklin.database.StacItemDao
import com.azavea.franklin.datamodel.MosaicDefinition
import com.azavea.franklin.datamodel.{CollectionsResponse, TileInfo}
import com.azavea.franklin.error.{NotFound => NF}
import com.azavea.franklin.extensions.validation._
import com.azavea.stac4s._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.syntax._
import org.http4s.dsl.Http4sDsl
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting.Standard
import sttp.client.{NothingT, SttpBackend}
import sttp.tapir.server.http4s._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class CollectionsService[F[_]: Concurrent](
    xa: Transactor[F],
    apiConfig: ApiConfig,
    collectionExtensionsRef: ExtensionRef[F, StacCollection]
)(
    implicit contextShift: ContextShift[F],
    timer: Timer[F],
    serverOptions: Http4sServerOptions[F],
    backend: SttpBackend[F, Nothing, NothingT],
    logger: Logger[F]
) extends Http4sDsl[F] {

  val apiHost            = apiConfig.apiHost
  val enableTransactions = apiConfig.enableTransactions
  val enableTiles        = apiConfig.enableTiles

  def listCollections(): F[Either[Unit, Json]] = {
    for {
      collections <- StacCollectionDao.listCollections().transact(xa)
      validators <- collections traverse { collection =>
        makeCollectionValidator(collection.stacExtensions, collectionExtensionsRef)
      }
    } yield {
      val updated = collections map { _.maybeAddTilesLink(enableTiles, apiHost) } map {
        _.updateLinksWithHost(apiConfig)
      }
      val validated = validators.zip(updated).map { case (f, v) => f(v) }
      val links = collections flatMap { collection =>
        collection.links.filter(_.rel == StacLinkType.Self) map { _.copy(rel = StacLinkType.Child) }
      }
      Either.right(CollectionsResponse(validated, links).asJson.dropNullValues)
    }
  }

  def getCollectionUnique(rawCollectionId: String): F[Either[NF, Json]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    for {
      collectionOption <- StacCollectionDao
        .getCollection(collectionId)
        .transact(xa)
      // it looks unnecessary to check enableTiles here given the logic below, but
      // we can skip the query if we know we don't need the mosaics
      mosaicDefinitions <- if (enableTiles) {
        MosaicDefinitionDao.listMosaicDefinitions(collectionId).transact(xa)
      } else {
        List.empty[MosaicDefinition].pure[F]
      }
      validatorOption <- collectionOption traverse { collection =>
        makeCollectionValidator(collection.stacExtensions, collectionExtensionsRef)
      }
    } yield {
      Either.fromOption(
        (collectionOption, validatorOption) mapN {
          case (collection, validator) =>
            validator(
              collection
                .maybeAddTilesLink(enableTiles, apiHost)
                .maybeAddMosaicLinks(enableTiles, apiHost, mosaicDefinitions)
                .updateLinksWithHost(apiConfig)
            ).asJson.dropNullValues
        },
        NF(s"Collection $collectionId not found")
      )
    }
  }

  def getCollectionTiles(rawCollectionId: String): F[Either[NF, (Json, String)]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    for {
      collectionOption <- StacCollectionDao
        .getCollection(collectionId)
        .transact(xa)
      mosaicDefinitions <- collectionOption.toList flatTraverse { _ =>
        MosaicDefinitionDao.listMosaicDefinitions(collectionId).transact(xa)
      }
    } yield {
      Either.fromOption(
        collectionOption.map(collection =>
          (
            TileInfo.fromStacCollection(apiHost, collection, mosaicDefinitions).asJson,
            collection.##.toString
          )
        ),
        NF(s"Collection $collectionId")
      )
    }
  }

  def createCollection(collection: StacCollection): F[Either[Unit, Json]] = {
    val newCollection = collection.copy(links =
      collection.links.filter({ link =>
        !Set[StacLinkType](StacLinkType.Item, StacLinkType.StacRoot, StacLinkType.Self)
          .contains(link.rel)
      }) ++
        List(
          StacLink(
            s"$apiHost/collections/${collection.id}",
            StacLinkType.Self,
            Some(`application/json`),
            collection.title
          ),
          StacLink(
            s"$apiHost",
            StacLinkType.StacRoot,
            Some(`application/json`),
            None
          )
        )
    )
    for {
      inserted  <- StacCollectionDao.insertStacCollection(newCollection, None).transact(xa)
      validator <- makeCollectionValidator(inserted.stacExtensions, collectionExtensionsRef)
    } yield Right(validator(inserted).asJson.dropNullValues)
  }

  def deleteCollection(rawCollectionId: String): F[Either[NF, Unit]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    for {
      collectionOption <- StacCollectionDao
        .getCollection(collectionId)
        .transact(xa)
      deleted <- collectionOption traverse { _ =>
        StacCollectionDao.query.filter(fr"id = $collectionId").delete.transact(xa).void
      }
    } yield {
      Either.fromOption(
        deleted,
        NF(s"Collection $collectionId not found")
      )
    }
  }

  def createMosaic(
      rawCollectionId: String,
      mosaicDefinition: MosaicDefinition
  ): F[Either[NF, MosaicDefinition]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

    (for {
      itemsInCollection <- StacItemDao.checkItemsInCollection(mosaicDefinition.items, collectionId)
      itemAssetValidity <- itemsInCollection flatTraverse { _ =>
        StacItemDao.checkAssets(mosaicDefinition.items, collectionId)
      }
      inserted <- itemAssetValidity traverse { _ =>
        MosaicDefinitionDao.insert(mosaicDefinition, collectionId)
      }
      _ <- (inserted, itemAssetValidity).tupled traverse {
        case (mosaic, assets) =>
          MosaicDefinitionDao.insertHistogram(mosaic.id, assets)
      }
    } yield inserted.leftMap({ err => NF(err.msg) })).transact(xa)
  }

  def getMosaic(
      rawCollectionId: String,
      mosaicId: UUID
  ): F[Either[NF, MosaicDefinition]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

    MosaicDefinitionDao.getMosaicDefinition(collectionId, mosaicId).transact(xa) map {
      Either.fromOption(_, NF())
    }
  }

  def deleteMosaic(
      rawCollectionId: String,
      mosaicId: UUID
  ): F[Either[NF, Unit]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)

    MosaicDefinitionDao.deleteMosaicDefinition(collectionId, mosaicId).transact(xa) map {
      case 0 => Left(NF())
      case _ => Right(())
    }
  }

  def listMosaics(
      rawCollectionId: String
  ): F[Either[NF, List[MosaicDefinition]]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    (for {
      collectionOption <- StacCollectionDao.getCollection(collectionId)
      mosaics <- collectionOption traverse { collection =>
        MosaicDefinitionDao.listMosaicDefinitions(collection.id)
      }
    } yield {
      mosaics match {
        case Some(mosaicDefinitions) => Right(mosaicDefinitions)
        case _                       => Left(NF())
      }
    }).transact(xa)

  }

  val collectionEndpoints =
    new CollectionEndpoints[F](enableTransactions, enableTiles, apiConfig.path)

  val routesList = List(
    Http4sServerInterpreter.toRoutes(collectionEndpoints.collectionsList)(_ => listCollections()),
    Http4sServerInterpreter.toRoutes(collectionEndpoints.collectionUnique)({
      case collectionId => getCollectionUnique(collectionId)
    })
  ) ++
    (if (enableTiles) {
       List(
         Http4sServerInterpreter.toRoutes(collectionEndpoints.collectionTiles)(getCollectionTiles),
         Http4sServerInterpreter
           .toRoutes(collectionEndpoints.createMosaic)(Function.tupled(createMosaic)),
         Http4sServerInterpreter
           .toRoutes(collectionEndpoints.getMosaic)(Function.tupled(getMosaic)),
         Http4sServerInterpreter
           .toRoutes(collectionEndpoints.deleteMosaic)(Function.tupled(deleteMosaic)),
         Http4sServerInterpreter.toRoutes(collectionEndpoints.listMosaics)(listMosaics)
       )
     } else Nil) ++
    (if (enableTransactions) {
       List(
         Http4sServerInterpreter.toRoutes(collectionEndpoints.createCollection)(collection =>
           createCollection(collection)
         ),
         Http4sServerInterpreter.toRoutes(collectionEndpoints.deleteCollection)(rawCollectionId =>
           deleteCollection(rawCollectionId)
         )
       )
     } else Nil)

  val routes = routesList.foldK

}
