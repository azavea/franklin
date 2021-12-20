---
id: configuration
title: Configuration
---

# Server Configuration

If you run the [startup command](./introduction#running-the-service) from the introduction, Franklin
will start with its default options. Only the default endpoints and the query extension
from the STAC API specification will be available. However, there are two additional flags you can
enable for additional functionality. In both cases, the additional endpoints will be included in
the server's OpenAPI specification at `<host>/open-api/spec.yaml`

## Configuring your database connection

You have two options for configuring the database. The first option is what's
shown in the example  [startup command](./introduction#running-the-service). In
that case, you specify a user, host, port, and password as separate arguments.

Alternatively, you can specify a complete connection string. That connection
string will look something like:

```
jdbc:postgresql://<host>/<database-name>?user=<user>&password=<password>
```

In general, it is safer to configure your database connection one component at a
time. That allows you to ensure that each value is individually correct and
leave assembling them into a strange looking string to Franklin. However,
there's one circumstance in which you should use the JDBC URL option instead. If
your cloud provider or internal IT systems have resulted in a database that
requires an SSL connection, you should use the JDBC URL configuration. You can
start the server in that case like:

```
application/run serve \
  --db-connection-string \
  jdbc:postgresql://localhost/franklin?user=franklin&password=franklin&ssl=true
```

This constraint was [originally
discovered](https://github.com/azavea/franklin/issues/669) in a Google Cloud
Platform environment but appears to be relevant in Azure settings as well.

## `--with-tiles`

Enabling the `--with-tiles` flag will add a few different sorts of tile rendering.

### Item raster tiles

Items with [`COG`](https://www.cogeo.org/) assets will get an additional link with the relationship
`tiles`. The tile information will include the available tile matrix sets (e.g., 
"WebMercatorQuad" for TMS services) and URL templates. Franklin will serve PNG tiles
at TMS URLs for these items.

### Collection item footprint tiles

Collections will get an additional link with the relationship `tiles`. This link will
include the same information as in item raster tiles, but instead of raster tiles, 
Franklin will serve
[MapBox vector tiles](https://docs.mapbox.com/vector-tiles/specification/) (MVT) of the footprints
of the items in that collection. For these MVTs, you can include a `withField`
query parameter which will properties from the items' labels that you can
then style in a client. You can pass multiple `withField` parameters to include multiple
fields in the vector tile output. Fields missing from the underlying items will not
be rendered into the resulting MVT.

### Label item data footprints

Label items imported from static catalogs will get an asset with the role `data-collection`.
This asset, because it's a collection, will also have footprints if you start the
server with the `--with-tiles` flag. For these MVTs, you can include a `withField`
query parameter which will properties from the items' labels that you can
then style in a client. You can pass multiple `withField` parameters to include multiple
fields in the vector tile output. Fields missing from the underlying items will not
be rendered into the resulting MVT.

## `--with-transactions`

Enabling the `--with-transactions` flag will add endpoints for creating, editing, and deleting items and
creating collections. These endpoints are useful for deployment scenarios where you
don't have all of the data you'll want to serve up front.

The transactions extension is documented in the [STAC API specification](https://github.com/radiantearth/stac-api-spec/tree/master/ogcapi-features/extensions/transaction).
To those, Franklin added an endpoint for creating collections as well.