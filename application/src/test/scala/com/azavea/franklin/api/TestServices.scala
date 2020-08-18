package com.azavea.franklin.api

import cats.effect.{ContextShift, Sync}
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.api.services.{CollectionItemsService, CollectionsService, SearchService}
import doobie.Transactor
import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}

class TestServices[F[_]: Sync](xa: Transactor[F])(implicit cs: ContextShift[F]) {

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
    new SearchService[F](apiConfig.apiHost, NonNegInt(30), apiConfig.enableTiles, xa)

  val collectionsService: CollectionsService[F] = new CollectionsService[F](
    xa,
    apiConfig
  )

  val collectionItemsService: CollectionItemsService[F] = new CollectionItemsService[F](
    xa,
    apiConfig
  )

}
