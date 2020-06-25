package com.azavea.franklin.api

import com.azavea.franklin.api.commands.ApiConfig
import cats.effect.{ContextShift, Sync}
import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}
import com.azavea.franklin.api.services.{CollectionItemsService, CollectionsService, SearchService}
import doobie.Transactor

class TestServices[F[_]: Sync](xa: Transactor[F])(implicit cs: ContextShift[F]) {

  val apiConfig: ApiConfig =
    ApiConfig(PosInt(9090), PosInt(9090), "localhost", "http", NonNegInt(30), true, false)

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
