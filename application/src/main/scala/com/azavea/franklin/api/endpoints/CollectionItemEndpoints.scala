package com.azavea.franklin.api.endpoints

import com.azavea.franklin.api.schemas._
import com.azavea.franklin.error.{
  CrudError,
  InvalidPatch,
  MidAirCollision,
  NotFound,
  ValidationError
}
import io.circe.{Codec => _, _}
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.model.StatusCode.{NotFound => NF, BadRequest, PreconditionFailed}
import com.azavea.stac4s.StacItem
import sttp.model.StatusCode

class CollectionItemEndpoints(enableTransactions: Boolean) {

  val base = endpoint.in("collections")

  val collectionItemsList: Endpoint[String, Unit, Json, Nothing] =
    base.get
      .in(path[String])
      .in("items")
      .out(jsonBody[Json])
      .description("A feature collection of collection items")
      .name("collectionItems")

  val collectionItemsUnique: Endpoint[(String, String), NotFound, (Json, String), Nothing] =
    base.get
      .in(path[String] / "items" / path[String])
      .out(jsonBody[Json])
      .errorOut(oneOf(statusMapping(NF, jsonBody[NotFound].description("not found"))))
      .out(header[String]("ETag"))
      .description("A single feature")
      .name("collectionItemUnique")

  val postItem: Endpoint[(String, StacItem), ValidationError, (Json, String), Nothing] =
    base.post
      .in(path[String] / "items")
      .in(jsonBody[StacItem])
      .out(jsonBody[Json])
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

  val putItem: Endpoint[(String, String, StacItem, String), CrudError, (Json, String), Nothing] =
    base.put
      .in(path[String] / "items" / path[String])
      .in(jsonBody[StacItem])
      .in(header[String]("If-Match"))
      .out(jsonBody[Json])
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

  val deleteItem: Endpoint[(String, String), Unit, Unit, Nothing] =
    base.delete
      .in(path[String] / "items" / path[String])
      .out(emptyOutput)
      .out(statusCode(StatusCode.NoContent))

  val patchItem: Endpoint[(String, String, Json, String), CrudError, (Json, String), Nothing] =
    base.patch
      .in(path[String] / "items" / path[String])
      .in(jsonBody[Json])
      .in(header[String]("If-Match"))
      .out(jsonBody[Json])
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

  val endpoints = List(collectionItemsList, collectionItemsUnique) ++
    (if (enableTransactions) transactionEndpoints else Nil)
}
