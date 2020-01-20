---
id: 0003-item-tms-rendering
title: 3 - Item TMS Rendering
---

Date: 2020-01-14

Status
------

Accepted

Context
-------

To make Franklin a useful tool for people working on machine learning
projects, we need not only to be able to serve json metadata about
imagery and labels, but also the imagery and label assets themselves.
This ADR will focus on two questions in particular in the domain of
serving imagery:

-   what should we render?
-   how should we render it?
-   how do users discover that they have something renderable?

### What should we render?

In a STAC API with many items, each of which may or may not have a COG,
we have some challenges deciding what we should make TMS endpoints
available for. We have a few options at different levels of user
interaction:

-   we could render just `Item`s -- in this case users don't get to
    choose the collection at all since there is no collection
-   we could render just all of the items in a `Collection` or all of
    the items in a `Catalog` -- in this case, once [transaction
    endpoints](https://github.com/azavea/franklin/issues/4) are
    implemented, it would be up to the user to create the container and
    put the items inside.
-   we could render arbitrary search results -- in this case the user
    would describe the sorts of items that they want to see, and we
    would provide a way to visualize their `ItemCollection` response.
    There wouldn't need to be anything persistent about the collection
    of items, though there would need to be something at least
    semi-persistent for the visualization of the collection of items.

Rendering collections is not a hard technical problem. However,
rendering collections also exposes us to risks of people returning items
with different sorts of imagery. For the short-term needs of viewing
images with their labels, this risk doesn't come with much of a benefit
attached to it. For that reason, we should render only single items for
now.

### How should we render single items?

For now our goal is only to support TMS rendering, which solves the
problem of how the data should be accessed, but doesn't solve the
problem of how to render the imagery.

#### Rendering labels

Since we're working on TMS rendering, we can only paint raster labels.
There are at least three possibilities for the state of those raster
labels: they can have three bands, one band, or some surprising number
of bands.

If the raster label has three bands, we don't need any help from the
user to make a decent effort at coloring it. We can assume the bands are
RGB, produce a PNG, ship it back, and walk away.

If the raster label has one band, the situation is more ambiguous. In
this case, it's probable that the labels are categorical. We can take a
first stab at coloring the image by using the `LabelItem`'s
`label:classes` property to determine the distinct classes, choose
random colors for them, and render the image with that color map. This
strategy exposes a new problem: we don't have anywhere currently to
persist the color map, so either the color map we choose will need to be
a query parameter somehow or we'll need to associate it with the `Item`
in the database. More detail on this problem is in the section on
[persisting styles](#persisting-styles). In any case, we have a strategy
available for coloring single-band labels as well.

If the raster label has some other number of bands, it's not obvious
what we should do. My instinct is that we should refuse to render it. I
don't think I can guess at this stage why someone would produce such a
label or what they want to do with it, so if it comes up we can listen
and see how we should incorporate this.

#### Rendering imagery

Several attempts have been made to standardize on an interface for
describing how to render imagery. In Raster Foundry we described a bunch
of imagery adjustment options with custom types for color ramps and
color palettes. Last summer someone proposed [an
extension](https://github.com/radiantearth/stac-spec/pull/470) to the
STAC specification to include visualization parameters based on their
Google Earth Engine experience. The OGC description of styles uses the
Styled Layer Descriptors (SLDs).

In the past, in particular on the District Builder project, getting up
to speed with SLDs wasn't easy for me. Getting just the essentials to
color some polygons was more of a challenge than I wanted it to be.
However, I think that using SLDs is the right move here for n reasons.

First, in District Builder, the SLDs were hand edited in a gigantic
config XML file. This made it easy to construct incomplete or invalid
objects. Since Franklin is written in Scala, we can capitalize on
[`scalaxb`](http://scalaxb.org/) to automatically generate the types
necessary not to write SLDs by hand. [This pull
request](https://github.com/geotrellis/geotrellis-server/pull/186) in
`geotrellis-server` adds the relevant XML schemas, and you can verify
that the types exist by publishing the `opengis` submodule locally and
importing from `opengis.sld._` in an ammonite repl.

Second, SLDs allow us to describe rendering strategies for vector and
raster data. While this ADR only addresses rendering raster data, we
will need to render vector data in the future, and not having to come up
with another rendering strategy will be helpful.

Third, the dialog between STAC and OGC about the direction geospatial
web services should take makes it likely that we'd have to justify not
using SLD if we chose something else. We're probably better off not
writing and forcing ourselves to champion usage of [the 15th
standard](https://xkcd.com/927/).

#### Persisting styles

Since users probably don't want to have to specify their visualization
parameters every time they look at a layer, we'll want to persist SLDs
for items somewhere. Right now, Franklin is backed by a PostgreSQL
database, but other backends are possible as well.

One option we have is to store style information in a separate table.
Storing styles in a separate table would open up the possibility of
conforming to the
[OGC Styles API specification](https://app.swaggerhub.com/apis/UAB-CREAF/opf-style-api/1.0.0#/sld-10).
The OGC Styles API prescribes a set of endpoints for creating and managing
styles, with either of SLD or mapbox styles. Adding endpoints for style
management would make API interaction slightly easier. Users would be able
to color style their raster (and later vector) layers in QGIS, export
the SLD, and POST to the API. The request after that would be to add
an asset to a STAC item, which would require `PATCH`ing an additional
asset instead of the full SLD document.

Our other two options are to store the SLD documents in properties on
items or in a separate column.
The downside of inserting the SLD directly into the item's properties is
that, since SLD is XML, we'll be mixing JSON and XML in JSON that people
generally expect to conform to a specification. The `properties` of STAC
items are free-form with a preference for avoiding nesting, and throwing
a potentially nested raw XML string into properties sounds like not a
great idea. However, we could provide a JSON codec for the types
generated from the SLD schema. The downside of throwing them into a
separate column is that it to some extent breaks our model of having ids,
foreign keys, and JSON documents as entire records in Postgres. Expanding
on the columns that represent an entity in conjunction with the entity's
JSON document makes Postgres less like other backends we might pursue.

The question is then whether we should attempt to develop in line with
the OGC API Styles specification.
The win we get from attempting to conform to the styles specification
is that we don't have to go out on a limb for the design of styles
management and justify choices we make that other people think are
weird. Since the previous section already says we should use SLD for
describing how to render, attempting to conform to the OGC Styles API
specification is a natural choice.

### How do users discover that there's a tile layer available for an item?

One question is whether tile URLs are links or assets. Since a links are
"link objects to resources and related URLs", and assets are "asset objects
that can be downloaded," tile layer URLs are more links than assets. There isn't
currently an
[IANA relation type](https://www.iana.org/assignments/link-relations/link-relations.xhtml)
for tiles. However, the
[Map Tiles API specification](https://github.com/opengeospatial/OGC-API-Maps/blob/master/standard/openapi/ogc-api-map-tiles.yaml#L97-L116)
does include a link with a `rel` of `tile`. The Map Tiles specification is
still a work in progress, but it's at least a sign of the direction OGC
is going thinking about how to advertise tiles for a collection. While
we're planning to serve tiles for items rather than collections, it's still
a reasonable source of inspiration.

Decision
--------

For now, in pursuit of our short-term goals and to get the rendering
machinery in place, we will render only items. For styling, we will use
SLDs, since they provide many styling options and are compatible with
other open source geospatial tools and standards. Styles will be persisted
in a separate table, and style management will occur through endpoints
as described by the OGC API Styles specification. Users will know they
can view a TMS layer for an item when it has a link with relation type
`tile`.

Consequences
------------

As a result of this ADR, we'll need to:

- familiarize ourselves with SLD
- open issues in the appropriate repositories for rendering entities with `TMSReification`
  and `HasRasterExtents` (the latter for histograms)
- add `geotrellis-server` as a dependency to Franklin and typeclass evidence for `TMSReification`
  and `HasRasterExtents` for `StacItem`s
- add an `extensions/` directory to Franklin including a STAC extension that our rendering strategy
  conforms to
- add a migration to add a `styles` table to the Franklin database
- add a `Dao` and routes for managing styles in conformance with the OGC API styles specifiation
- add routes for serving imagery from items and a command line argument to the Franklin server
  to activate them
