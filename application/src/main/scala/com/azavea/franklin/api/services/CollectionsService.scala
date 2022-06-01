package com.azavea.franklin.api.services

import cats.data.EitherT
import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.all._
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.api.endpoints.CollectionEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.PGStacQueries
import com.azavea.franklin.datamodel.{CollectionsResponse, MosaicDefinition, TileInfo}
import com.azavea.franklin.datamodel.stactypes.Collection
import com.azavea.franklin.error.{NotFound => NF}
import com.azavea.franklin.extensions.validation._
import com.azavea.stac4s._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import eu.timepit.refined.auto._
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.syntax._
import monocle.syntax.all._
import org.http4s.dsl.Http4sDsl
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting.Standard
import sttp.client.{NothingT, SttpBackend}
import sttp.tapir.server.http4s._

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets
import java.util.UUID

case class AddCollectionLinks(apiConfig: ApiConfig) {

  val _collectionId = root.id.string

  def createSelfLink(collection: Collection): StacLink = {
    StacLink(
      s"${apiConfig.apiHost}/collections/${collection.id}",
      StacLinkType.Self,
      Some(`application/json`),
      None
    )
  }

  def apply(collection: Collection) = {
    val selfLink = createSelfLink(collection)
    collection.copy(links=collection.links :+ selfLink)
  }
}

class CollectionsService[F[_]: Concurrent](
    xa: Transactor[F],
    apiConfig: ApiConfig,
)(
    implicit contextShift: ContextShift[F],
    timer: Timer[F],
    serverOptions: Http4sServerOptions[F],
    backend: SttpBackend[F, Nothing, NothingT],
    logger: Logger[F]
) extends Http4sDsl[F] {

  val apiHost            = apiConfig.apiHost
  val enableTransactions = apiConfig.enableTransactions
  val addCollectionLinks = AddCollectionLinks(apiConfig)

  def listCollections(): F[Either[Unit, CollectionsResponse]] = {
    for {
      collections <- PGStacQueries.listCollections().transact(xa)
      collectionsWithLinks = collections.map(addCollectionLinks(_))
    } yield {
      val childLinks: List[StacLink] = collectionsWithLinks.map({ coll: Collection =>
        StacLink(
          s"${apiConfig.apiHost}/collections/${coll.id}",
          StacLinkType.Child,
          Some(`application/json`),
          None
        )
      })
      Either.right(CollectionsResponse(collections, childLinks))
    }
  }

  def getCollectionUnique(rawCollectionId: String): F[Either[NF, Collection]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    for {
      collection <- PGStacQueries
        .getCollection(collectionId)
        .transact(xa)
      //collectionWithLinks = collections.map(addCollectionLinks(_))
    } yield {
      Either.fromOption(
        collection,
        //collectionWithLinks.map(_.dropNullValues),
        NF(s"Collection $collectionId not found")
      )
    }
  }


  def postCollection(collection: Collection): F[Either[Unit, Collection]] = {
    for {
      _ <- PGStacQueries.createCollection(collection).transact(xa)
    } yield {
      Right(collection)
    }
  }

  def putCollection(collection: Collection): F[Either[Unit, Collection]] = {
    for {
      _ <- PGStacQueries.updateCollection(collection).transact(xa)
    } yield {
      Right(collection)
    }
  }
  // {
  //   val newCollection = collection.copy(links =
  //     collection.links.filter({ link =>
  //       !Set[StacLinkType](StacLinkType.Item, StacLinkType.StacRoot, StacLinkType.Self)
  //         .contains(link.rel)
  //     }) ++
  //       List(
  //         StacLink(
  //           s"$apiHost/collections/${collection.id}",
  //           StacLinkType.Self,
  //           Some(`application/json`),
  //           collection.title
  //         ),
  //         StacLink(
  //           s"$apiHost",
  //           StacLinkType.StacRoot,
  //           Some(`application/json`),
  //           None
  //         )
  //       )
  //   )
  //   for {
  //     inserted  <- StacCollectionDao.insertStacCollection(newCollection, None).transact(xa)
  //     validator <- makeCollectionValidator(inserted.stacExtensions, collectionExtensionsRef)
  //   } yield Right(validator(inserted).asJson.dropNullValues)
  // }

  def deleteCollection(rawCollectionId: String): F[Either[NF, Unit]] = ???
  // {
  //   val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
  //   for {
  //     collectionOption <- StacCollectionDao
  //       .getCollection(collectionId)
  //       .transact(xa)
  //     deleted <- collectionOption traverse { _ =>
  //       StacCollectionDao.query.filter(fr"id = $collectionId").delete.transact(xa).void
  //     }
  //   } yield {
  //     Either.fromOption(
  //       deleted,
  //       NF(s"Collection $collectionId not found")
  //     )
  //   }
  // }

  val collectionEndpoints =
    new CollectionEndpoints[F](enableTransactions, apiConfig.path)

  val routesList = List(
    Http4sServerInterpreter.toRoutes(collectionEndpoints.collectionsList)(_ => listCollections()),
    Http4sServerInterpreter.toRoutes(collectionEndpoints.collectionUnique)({
      case collectionId => getCollectionUnique(collectionId)
    })
  ) ++
    (if (enableTransactions) {
       List(
         Http4sServerInterpreter.toRoutes(collectionEndpoints.postCollection)(collection =>
           postCollection(collection)
         ),
         Http4sServerInterpreter.toRoutes(collectionEndpoints.putCollection)(collection =>
           putCollection(collection)
         ),
         Http4sServerInterpreter.toRoutes(collectionEndpoints.deleteCollection)(rawCollectionId =>
           deleteCollection(rawCollectionId)
         )
       )
     } else Nil)

  val routes = routesList.foldK

}
