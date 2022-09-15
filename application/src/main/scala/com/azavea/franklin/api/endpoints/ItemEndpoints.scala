package com.azavea.franklin.api.endpoints

import cats.effect.Concurrent
import com.azavea.franklin.api.FranklinJsonPrinter._
import com.azavea.franklin.api.schemas._
import com.azavea.franklin.datamodel.IfMatchMode
import com.azavea.franklin.datamodel.StacSearchCollection
import com.azavea.franklin.error.{
  CrudError,
  InvalidPatch,
  MidAirCollision,
  NotFound,
  ValidationError
}
import com.azavea.stac4s.StacItem
import io.circe.{Codec => _, _}
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.model.StatusCode.{NotFound => NF, BadRequest, PreconditionFailed}
import sttp.tapir._
import sttp.tapir.codec.refined._
import sttp.tapir.generic.auto._

class ItemEndpoints[F[_]: Concurrent](
    defaultLimit: Int,
    enableTransactions: Boolean,
    pathPrefix: Option[String]
) {

  val basePath = baseFor(pathPrefix, "collections")

  val base = endpoint.in(basePath)

  val itemsList: Endpoint[
    (String, Option[String], Option[Int]),
    Unit,
    StacSearchCollection,
    Fs2Streams[F]
  ] =
    base.get
      .in(path[String])
      .in("items")
      .in(
        query[Option[String]]("token")
          .description("Token to retrieve the next page of items")
      )
      .in(
        query[Option[Int]]("limit")
          .description(s"How many items to return. Defaults to ${defaultLimit}")
      )
      .out(jsonBody[StacSearchCollection])
      .description("A feature collection of collection items")
      .name("items")

  val itemsUnique: Endpoint[(String, String), NotFound, (StacItem, String), Fs2Streams[F]] =
    base.get
      .in(path[String] / "items" / path[String])
      .out(jsonBody[StacItem])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .out(header[String]("ETag"))
      .description("A single feature")
      .name("itemUnique")

  val postItem: Endpoint[(String, StacItem), ValidationError, (StacItem, String), Fs2Streams[F]] =
    base.post
      .in(path[String] / "items")
      .in(accumulatingJsonBody[StacItem])
      .out(jsonBody[StacItem])
      .out(header[String]("ETag"))
      .errorOut(
        oneOf(
          statusMapping(
            BadRequest,
            jsonBody[ValidationError]
              .description("Collection in route did not match collection in item")
          )
        )
      )
      .description("Create a new feature in a collection")
      .name("postItem")

  val putItem
      : Endpoint[(String, String, StacItem, IfMatchMode), CrudError, (StacItem, String), Fs2Streams[
        F
      ]] =
    base.put
      .in(path[String] / "items" / path[String])
      .in(accumulatingJsonBody[StacItem])
      .in(header[IfMatchMode]("If-Match"))
      .out(jsonBody[StacItem])
      .out(header[String]("ETag"))
      .errorOut(
        oneOf[CrudError](
          statusMapping(
            BadRequest,
            jsonBody[ValidationError]
              .description("Something was wrong with the body of the request")
          ),
          statusMapping(
            NF,
            jsonBody[NotFound].description("not found")
          ),
          statusMapping(
            PreconditionFailed,
            jsonBody[MidAirCollision]
              .description("Your state of the item is stale. Refresh the item and try again.")
          )
        )
      )

  val deleteItem: Endpoint[(String, String), Unit, Unit, Fs2Streams[F]] =
    base.delete
      .in(path[String] / "items" / path[String])
      .out(emptyOutput)
      .out(statusCode(StatusCode.NoContent))

  val patchItem
      : Endpoint[(String, String, Json, IfMatchMode), CrudError, (StacItem, String), Fs2Streams[
        F
      ]] =
    base.patch
      .in(path[String] / "items" / path[String])
      .in(accumulatingJsonBody[Json])
      .in(header[IfMatchMode]("If-Match"))
      .out(jsonBody[StacItem])
      .out(header[String]("ETag"))
      .errorOut(
        oneOf[CrudError](
          statusMapping(
            PreconditionFailed,
            jsonBody[MidAirCollision]
              .description("Your state of the item is stale. Refresh the item and try again.")
          ),
          statusMapping(
            NF,
            jsonBody[NotFound].description("not found")
          ),
          statusMapping(
            BadRequest,
            jsonBody[InvalidPatch]
              .description("Applying this patch would result in an invalid STAC Item")
          )
        )
      )

  val transactionEndpoints = List(
    postItem,
    putItem,
    patchItem,
    deleteItem
  )

  val endpoints = List(itemsList, itemsUnique) ++
    (if (enableTransactions) transactionEndpoints else Nil)
}
