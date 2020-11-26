package com.azavea.franklin.datamodel

import com.azavea.stac4s.StacCollection
import com.azavea.stac4s.TwoDimBbox
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.JsonCodec

/** Product representation of the TileJson specification:
  *
  * https://github.com/mapbox/tilejson-spec/tree/master/2.3.0
  *
  * Included comments are inlined from field descriptions at the
  * above link. Fields are reordered for sensible order of argument
  * application.
  */
@JsonCodec
final case class TileJson(
    // OPTIONAL. Default: null. A name describing the tileset. The name can
    // contain any legal character. Implementations SHOULD NOT interpret the
    // name as HTML.
    name: Option[String],
    // OPTIONAL. Default: null. A text description of the tileset. The
    // description can contain any legal character. Implementations SHOULD NOT
    // interpret the description as HTML.
    description: Option[String],
    // REQUIRED. An array of tile endpoints. {z}, {x} and {y}, if present,
    // are replaced with the corresponding integers. If multiple endpoints are specified, clients
    // may use any combination of endpoints. All endpoints MUST return the same
    // content for the same URL. The array MUST contain at least one endpoint.
    tiles: List[String],
    // OPTIONAL. Default: "xyz". Either "xyz" or "tms". Influences the y
    // direction of the tile coordinates.
    // The global-mercator (aka Spherical Mercator) profile is assumed.
    scheme: Option[String] = Some("tms"),
    // OPTIONAL. Default: 0. >= 0, <= 30.
    // An integer specifying the minimum zoom level.
    minzoom: Int = 0,
    // OPTIONAL. Default: 30. >= 0, <= 30.
    // An integer specifying the maximum zoom level. MUST be >= minzoom.
    maxzoom: Int = 30,
    // OPTIONAL. Default: [-180, -90, 180, 90].
    // The maximum extent of available map tiles. Bounds MUST define an area
    // covered by all zoom levels. The bounds are represented in WGS:84
    // latitude and longitude values, in the order left, bottom, right, top.
    // Values may be integers or floating point numbers.
    bounds: Option[TwoDimBbox] = None,
    // OPTIONAL. Default: null.
    // The first value is the longitude, the second is latitude (both in
    // WGS:84 values), the third value is the zoom level as an integer.
    // Longitude and latitude MUST be within the specified bounds.
    // The zoom level MUST be between minzoom and maxzoom.
    // Implementations can use this value to set the default location. If the
    // value is null, implementations may use their own algorithm for
    // determining a default location.
    center: Option[(Double, Double)] = None,
    // OPTIONAL. Default: null. Contains a legend to be displayed with the map.
    // Implementations MAY decide to treat this as HTML or literal text.
    // For security reasons, make absolutely sure that this field can't be
    // abused as a vector for XSS or beacon tracking.
    legend: Option[String] = None,
    // OPTIONAL. Default: "1.0.0". A semver.org style version number. When
    // changes across tiles are introduced, the minor version MUST change.
    // This may lead to cut off labels. Therefore, implementors can decide to
    // clean their cache when the minor version changes. Changes to the patch
    // level MUST only have changes to tiles that are contained within one tile.
    // When tiles change significantly, the major version MUST be increased.
    // Implementations MUST NOT use tiles with different major versions.
    version: String = "0.0.1",
    // REQUIRED. A semver.org style version number. Describes the version of
    // the TileJSON spec that is implemented by this JSON object.
    tileJson: String = "2.2.0",
    // OPTIONAL. Default: null. Contains an attribution to be displayed
    // when the map is shown to a user. Implementations MAY decide to treat this
    // as HTML or literal text. For security reasons, make absolutely sure that
    // this field can't be abused as a vector for XSS or beacon tracking.
    attribution: Option[String] = None,
    // OPTIONAL. Default: null. Contains a mustache template to be used to
    // format data from grids for interaction.
    // See https://github.com/mapbox/utfgrid-spec/tree/master/1.2
    // for the interactivity specification.
    template: Option[String] = None,
    // OPTIONAL. Default: []. An array of interactivity endpoints. {z}, {x}
    // and {y}, if present, are replaced with the corresponding integers. If multiple
    // endpoints are specified, clients may use any combination of endpoints.
    // All endpoints MUST return the same content for the same URL.
    // If the array doesn't contain any entries, interactivity is not supported
    // for this tileset.
    // See https://github.com/mapbox/utfgrid-spec/tree/master/1.2
    // for the interactivity specification.
    grids: List[String] = Nil,
    // OPTIONAL. Default: []. An array of data files in GeoJSON format.
    // {z}, {x} and {y}, if present,
    // are replaced with the corresponding integers. If multiple
    // endpoints are specified, clients may use any combination of endpoints.
    // All endpoints MUST return the same content for the same URL.
    // If the array doesn't contain any entries, then no data is present in
    // the map.
    data: List[String] = Nil
)

object TileJson {

  def fromStacCollection(collection: StacCollection, serverHost: NonEmptyString): TileJson =
    TileJson(
      collection.title,
      Some(collection.description),
      List(s"$serverHost/tiles/collections/${collection.id}/footprint/WebMercatorQuad/{z}/{x}/{y}"),
      scheme = Some("xyz")
    )
}
