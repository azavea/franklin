package com.azavea.franklin.api.services

import cats.data.EitherT
import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.all._
import com.azavea.franklin.commands.ApiConfig
import com.azavea.franklin.api.endpoints.CollectionEndpoints
import com.azavea.franklin.api.implicits._
import com.azavea.franklin.database.PGStacQueries
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
import io.circe.optics.JsonPath._
import io.circe.syntax._
import monocle.syntax.all._
import org.http4s.dsl.Http4sDsl
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting.Standard
import sttp.client.{NothingT, SttpBackend}
import sttp.tapir.server.http4s._

import java.net.{URLEncoder, URLDecoder}
import java.nio.charset.StandardCharsets
import java.util.UUID

case class AddCollectionLinks(apiConfig: ApiConfig) {

  val _collectionId = root.id.string

  def _addLink(link: StacLink) = root.links.arr.modify({ ls: Vector[Json] => ls :+ link.asJson})

  def addTileLink(collection: Json): Json = {
    val encodedCollectionId =
      URLEncoder.encode(_collectionId.getOption(collection).get, StandardCharsets.UTF_8.toString)
    val tileLink = StacLink(
      s"${apiConfig.apiHost}/collections/$encodedCollectionId/tiles",
      StacLinkType.VendorLinkType("tiles"),
      Some(`application/json`),
      Some("Tile URLs for Collection")
    )
    _addLink(tileLink)(collection)
  }

  def addSelfLink(collection: Json): Json = {
    val encodedCollectionId =
      URLEncoder.encode(_collectionId.getOption(collection).get, StandardCharsets.UTF_8.toString)
    val selfLink = StacLink(
      s"${apiConfig.apiHost}/collections/$encodedCollectionId",
      StacLinkType.Self,
      Some(`application/json`),
      None
    )
    _addLink(selfLink)(collection)
  }

  def apply(collection: Json) = {
    (addSelfLink _  compose addTileLink)(collection)
  }
}

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
  val addCollectionLinks = AddCollectionLinks(apiConfig)

  def listCollections(): F[Either[Unit, Json]] = {
    for {
      collections <- PGStacQueries.listCollections().transact(xa)
      collectionsWithLinks = collections.map(addCollectionLinks(_))
    } yield {
      val childrenLinks: List[Json] = collectionsWithLinks.map({ coll: Json =>
        val collId = root.id.string.getOption(coll).get
        val encodedCollectionId =
          URLEncoder.encode(collId, StandardCharsets.UTF_8.toString)
        StacLink(
          s"${apiConfig.apiHost}/collections/$encodedCollectionId",
          StacLinkType.Child,
          Some(`application/json`),
          None
        ).asJson
      })
      Either.right(CollectionsResponse(collectionsWithLinks, childrenLinks).asJson.dropNullValues)
    }
  }

  def getCollectionUnique(rawCollectionId: String): F[Either[NF, Json]] = {
    val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
    for {
      collections <- PGStacQueries
        .getCollection(collectionId)
        .transact(xa)
      collectionWithLinks = collections.map(addCollectionLinks(_))
    } yield {
      Either.fromOption(
        collectionWithLinks.map(_.dropNullValues),
        NF(s"Collection $collectionId not found")
      )
    }
  }

  // Old implementation (can delete once things work)
  // def getCollectionUnique(rawCollectionId: String): F[Either[NF, Json]] = {
  //   val collectionId = URLDecoder.decode(rawCollectionId, StandardCharsets.UTF_8.toString)
  //   for {
  //     collectionOption <- StacCollectionDao
  //       .getCollection(collectionId)
  //       .transact(xa)
  //     // it looks unnecessary to check enableTiles here given the logic below, but
  //     // we can skip the query if we know we don't need the mosaics
  //     mosaicDefinitions <- if (enableTiles) {
  //       MosaicDefinitionDao.listMosaicDefinitions(collectionId).transact(xa)
  //     } else {
  //       List.empty[MosaicDefinition].pure[F]
  //     }
  //     validatorOption <- collectionOption traverse { collection =>
  //       makeCollectionValidator(collection.stacExtensions, collectionExtensionsRef)
  //     }
  //   } yield {
  //     Either.fromOption(
  //       (collectionOption, validatorOption) mapN {
  //         case (collection, validator) =>
  //           validator(
  //             collection
  //               .maybeAddTilesLink(enableTiles, apiHost)
  //               .maybeAddMosaicLinks(enableTiles, apiHost, mosaicDefinitions)
  //               .updateLinksWithHost(apiConfig)
  //           ).asJson.dropNullValues
  //       },
  //       NF(s"Collection $collectionId not found")
  //     )
  //   }
  // }

  def createCollection(collection: StacCollection): F[Either[Unit, Json]] = ???
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
