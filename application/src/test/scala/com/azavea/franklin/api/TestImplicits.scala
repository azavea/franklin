package com.azavea.franklin.api

import cats.Applicative
import cats.effect.{Concurrent, ContextShift, Timer}
import cats.syntax.functor._
import com.azavea.franklin.api.commands.ApiConfig
import com.azavea.franklin.api.services.{CollectionItemsService, CollectionsService, SearchService}
import doobie.Transactor
import eu.timepit.refined.types.numeric.{NonNegInt, PosInt}
import org.typelevel.log4cats.noop.NoOpLogger
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

trait TestImplicits[F[_]] {

  implicit def sttpBackend(implicit concurrent: Concurrent[F]) =
    AsyncHttpClientCatsBackend.stub[F].whenAnyRequest.thenRespondOk()

  implicit def unsafeLogger(implicit concurrent: Concurrent[F]) = NoOpLogger.impl[F]
}
