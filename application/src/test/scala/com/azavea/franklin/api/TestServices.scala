package com.azavea.franklin.api

import cats.Applicative
import cats.effect.{Concurrent, ContextShift, Timer}
import cats.syntax.functor._
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.api.services.{CollectionItemsService, CollectionsService, SearchService}
import com.azavea.franklin.extensions.validation.{collectionExtensionsRef, itemExtensionsRef}
import com.azavea.stac4s.{`application/json`, StacLink, StacLinkType}
import doobie.Transactor
import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}
import io.chrisdavenport.log4cats.noop.NoOpLogger
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

class TestServices[F[_]: Concurrent](xa: Transactor[F])(
    implicit cs: ContextShift[F],
    timer: Timer[F]
) extends TestImplicits[F] {

  val rootLink = StacLink(
    "http://bogus.com",
    StacLinkType.StacRoot,
    Some(`application/json`),
    None
  )

  val apiConfig: ApiConfig =
    ApiConfig(
      PosInt(9090),
      PosInt(9090),
      "localhost",
      None,
      "http",
      NonNegInt(30),
      true,
      false,
      false
    )

  val searchService: SearchService[F] =
    new SearchService[F](apiConfig, NonNegInt(30), apiConfig.enableTiles, xa, rootLink)

  val collectionsService: F[CollectionsService[F]] = collectionExtensionsRef[F] map { ref =>
    new CollectionsService[F](
      xa,
      apiConfig,
      ref
    )
  }

  val collectionItemsService: F[CollectionItemsService[F]] = itemExtensionsRef[F] map { ref =>
    new CollectionItemsService[F](
      xa,
      apiConfig,
      ref,
      rootLink
    )
  }

}
