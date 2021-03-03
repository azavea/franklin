package com.azavea.franklin.api.endpoints

import cats.data.NonEmptyList
import cats.effect.Concurrent
import com.azavea.franklin.api.schemas._
import com.azavea.franklin.datamodel.CollectionItemsResponse
import com.azavea.franklin.error.{CrudError, NotFound, ValidationError}
import com.azavea.stac4s.extensions.layer.StacLayer
import com.azavea.stac4s.{StacCatalog, StacItem}
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode.{NotFound => NF, BadRequest}
import sttp.tapir._
import sttp.tapir.codec.refined._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import eu.timepit.refined.types.string

class LayerEndpoints[F[_]: Concurrent](enableLayers: Boolean) {
  val basePath = endpoint.in("layers")

  val listLayers =
    basePath
      .out(jsonBody[StacCatalog])
      .description("List available ad hoc groups of items called \"layers\"")

  val createLayer = basePath.post
    .in(jsonBody[StacLayer])
    .out(jsonBody[StacLayer])
    .description("Create a new STAC Layer for ad hoc groups of items called a \"layer\"")

  val getLayer = basePath.get
    .in(path[String])
    .out(jsonBody[StacLayer])
    .errorOut(oneOf[CrudError](statusMapping(NF, jsonBody[NotFound].description("not found"))))
    .description("Fetch a specific STAC layer")

  val deleteLayer = basePath.delete.errorOut(
    oneOf[CrudError](statusMapping(NF, jsonBody[NotFound].description("not found")))
  )

  val getLayerItems = basePath.get
    .in(path[String] / "items")
    .out(jsonBody[CollectionItemsResponse])
    .errorOut(oneOf[CrudError](statusMapping(NF, jsonBody[NotFound].description("not found"))))
    .description("Fetch paginated items in a STAC layer")

  val addLayerItems = basePath.post
    .in(path[String] / "items")
    .in(jsonBody[NonEmptyList[String]].description("Item IDs to add to this layer"))
    .out(jsonBody[StacLayer])
    .errorOut(oneOf[CrudError](statusMapping(NF, jsonBody[NotFound].description("not found"))))
    .description("Add a non-empty list of items to this layer by ID")

  val replaceLayerItems = basePath.put
    .in(path[String] / "items")
    .in(
      jsonBody[NonEmptyList[String]]
        .description("Item IDs to use to replace items currently in this layer")
    )
    .out(jsonBody[StacLayer])
    .description("Replace items in this layer with other items by ID")

  val getLayerItem = basePath.get
    .in(path[String] / "items" / path[String])
    .out(jsonBody[StacItem])
    .errorOut(
      oneOf[CrudError](
        statusMapping(
          NF,
          jsonBody[NotFound].description("Item not in layer or layer does not exist")
        )
      )
    )
    .description("Get a specific item in this layer")

  val removeLayerItem = basePath.delete
    .in(path[String] / "items" / path[String])
    .errorOut(
      oneOf[CrudError](
        statusMapping(
          NF,
          jsonBody[NotFound].description("Item not in layer or layer does not exist")
        ),
        statusMapping(
          BadRequest,
          jsonBody[ValidationError].description("Cannot remove last item from a layer")
        )
      )
    )
    .out(jsonBody[StacLayer])

  val allEndpoints = if (enableLayers) {
    NonEmptyList.of(
      listLayers,
      createLayer,
      getLayer,
      deleteLayer,
      getLayerItems,
      addLayerItems,
      replaceLayerItems,
      getLayerItem,
      removeLayerItem
    )
  }
}
