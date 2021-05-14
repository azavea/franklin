package com.azavea.franklin.api.services

import cats.effect._
import cats.syntax.all._
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.api.endpoints.CollectionEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.StacCollectionDao
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
import sttp.client.{NothingT, SttpBackend}
import sttp.tapir.server.http4s._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import cats.effect.Temporal

class CollectionsService[F[_]: Concurrent](
    xa: Transactor[F],
    apiConfig: ApiConfig,
    collectionExtensionsRef: ExtensionRef[F, StacCollection]
)(
    implicit contextShift: ContextShift[F],
    timer: Temporal[F],
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
      validatorOption <- collectionOption traverse { collection =>
        makeCollectionValidator(collection.stacExtensions, collectionExtensionsRef)
      }
    } yield {
      Either.fromOption(
        (collectionOption, validatorOption) mapN {
          case (collection, validator) =>
            validator(
              collection.maybeAddTilesLink(enableTiles, apiHost).updateLinksWithHost(apiConfig)
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
    } yield {
      Either.fromOption(
        collectionOption.map(collection =>
          (
            TileInfo.fromStacCollection(apiHost, collection).asJson,
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

  val collectionEndpoints = new CollectionEndpoints[F](enableTransactions, enableTiles)

  val routesList = List(
    Http4sServerInterpreter.toRoutes(collectionEndpoints.collectionsList)(_ => listCollections()),
    Http4sServerInterpreter.toRoutes(collectionEndpoints.collectionUnique)({
      case collectionId => getCollectionUnique(collectionId)
    })
  ) ++
    (if (enableTiles) {
       List(
         Http4sServerInterpreter.toRoutes(collectionEndpoints.collectionTiles)(getCollectionTiles)
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
