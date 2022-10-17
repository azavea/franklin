---
id: introduction
title: Introduction
---

# `franklin`

## What's `franklin`?

A [STAC](https://github.com/radiantearth/stac-spec) and [OGC API Features](http://docs.opengeospatial.org/is/17-069r3/17-069r3.html) compliant web service focused on ease-of-use for end-users.

`franklin` imports and serves STAC catalogs, storing data in Postgres. Its goal is to enable import and querying of STAC catalogs as simple as possible

## How do I get it?

`franklin` publishes [docker](https://quay.io/repository/azavea/franklin?tab=tags) containers at available publicly that can be deployed anywhere that docker can be deployed. To run the container you need to supply a postgres database for `franklin` to connect to.

## Running `franklin`

Running `franklin` requires only one command:
 - `serve` to start the API server

### Database configuration
By default  `franklin` attempts to connect to a database named `franklin` at `localhost:5432` with a password of `franklin`. Using the following CLI options you can customize your database connection*:

```bash
    --db-user <string>
        User to connect with database with
    --db-password <string>
        Database password to use
    --db-host <string>
        Database host to connect to
    --db-port <integer>
        Port to connect to database on
    --db-name <string>
        Database name to connect to
```

**Database connection options can also be set using environment variables: `DB_USER`, `DB_PASSWORD`, `DB_HOST`, `DB_PORT`, `DB_NAME`*

### Example commands

The following examples show how to get started with `franklin` by importing a local catalog.

#### Starting a database

The first step is to find a database to connect to. This is handled for us by pgstac. Bringing up a new pgstac database can be done through docker-compose. No migrations are necessary. The bare pgstac image includes them already.

```bash
docker-compose up pgstac
```

This starts a database container (`pgstac`) in the background. If you want to see that it is still running you can use the `docker ps` command. If you want to stop it at some point you can use the command `docker stop pgstac`.

#### Importing data

Cars need fuel to run and `franklin` needs data to be useful. Pgstac comes with a small python library aimed at simplifying data imports. The example import provided with this library for development purposes loads a collection of NOAA imagery centered on Joplin, Missouri with the id "joplin". To carry out the example import, run `scripts/ingest_data` while the `pgstac` container is running

```bash
./scripts/ingest_data
```

#### Running the service

At this point you are ready to run the service. To start the service on the default port `9090` you can run the `serve` command using `docker run`:

```bash
docker run \
  --link franklin-database:franklin-database \
  -p 9090:9090 \
  quay.io/azavea/franklin:latest \
  serve \
  --db-user benjamin \
  --db-name franklin \
  --db-password franklinsecret \
  --db-host franklin-database
```

Additional API options and functionality can also be configured via the `franklin` CLI* by passing options to the `serve` command:

```
  --external-port 
      Port users/clients hit for requests
  --internal-port 
      Port server listens on, this will be different from 'external-port' when service is started behind a proxy
  --api-host 
      Hostname Franklin is hosted it (e.g. localhost)
  --api-path 
      Path component for root of Franklin instance (e.g. /stac/api)
  --api-scheme 
      Scheme server is exposed to end users with
  --default-limit 
      Default limit for items returned in paginated responses
  --with-transactions
      Whether to respond to transaction requests, like adding or updating items
  --with-tiles
      Whether to include tile endpoints
```

**API options can also be set using environment variables: `API_EXTERNAL_PORT`, `API_INTERNAL_PORT`, `API_HOST`, `API_PATH`, `API_SCHEME`, `API_DEFAULT_LIMIT`, `API_WITH_TRANSACTIONS`, `API_WITH_TILES`*


### Using docker compose

The examples above illustrate the separate commands and some options that can be used when running `franklin` via `docker run` commands. However, if you are familiar with `docker-compose` the configuration below can be used to get you started very quickly.

First, copy this file locally in a `docker-compose.yml` file:
```yaml
version: '2.3'
services:
  database:
    image: quay.io/azavea/postgis:2.3-postgres9.6-slim
    environment:
      - POSTGRES_USER=franklin
      - POSTGRES_PASSWORD=franklin
      - POSTGRES_DB=franklin
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "franklin"]
      interval: 3s
      timeout: 3s
      retries: 3
      start_period: 5s

  franklin:
    image: quay.io/azavea/franklin:latest
    depends_on:
      database:
        condition: service_healthy
	command:
	  - serve
    volumes:
      - ./:/opt/franklin/
    environment:
      - ENVIRONMENT=development
      - DB_HOST=database.service.internal
      - DB_NAME=franklin
      - DB_USER=franklin
      - DB_PASSWORD=franklin
      - AWS_PROFILE
      - AWS_REGION
    links:
      - database:database.service.internal
    ports:
      - "9090:9090"
```

Second, you can run `docker-compose run franklin migrate` to set up the database. Next, you can import a local dataset by copying it to the same directory as the `docker-compose.yml` file you created. Assuming that the root catalog is `catalog.json` the command would be `docker-compose run franklin import-catalog --catalog-root /opt/franklin/catalog.json`. Lastly, once the import is finished you can start the webserver and go to [`localhost:9090`](http://localhost:9090) to view your catalog.
