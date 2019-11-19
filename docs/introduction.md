# `franklin`

## What's `franklin`?

A [STAC](https://github.com/radiantearth/stac-spec) and [OGC API Features](http://docs.opengeospatial.org/is/17-069r3/17-069r3.html) compliant web service focused on ease-of-use for end-users.

`franklin` imports and serves STAC catalogs, storing data in Postgres. Its goal is to enable import and querying of STAC catalogs as simple as possible

## How do I get it?

`franklin` publishes [docker](https://quay.io/repository/azavea/franklin?tab=tags) containers at available publicly that can be deployed anywhere that docker can be deployed. To run the container you need to supply a postgres database for `franklin` to connect to.

## Running `franklin`

`franklin` consists of three commands:
 - `import` to take an existing catalog and add it to the database
 - `migrate` to run migrations to set up the database
 - `serve` to start the API server

### Database configuration
By default  `franklin` attempts to connect to a database named `franklin` at `localhost:5432` with a password of `franklin`. Using the following CLI options you can customize your database connection:

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

### Example commands

The following examples show how to get started with `franklin` by importing a local catalog.

#### Starting a database

The first step is to find a database to connect to. If you already have a Postgres instance with PostGIS installed you can skip this step. If you do not have a database, don't worry - it's easy to get one started usind docker.

Azavea maintains [images](https://quay.io/repository/azavea/postgis?tab=tags) with PostGIS installed that are convenient to use for development. In this case we will start a container with Postgres 11 and PostGIS 2.5 installed.

```bash
docker run -d -p 5432:5432 \
  --name franklin-database \
  -e POSTGRES_PASSWORD=franklinsecret \
  -e POSTGRES_USER=benjamin \
  -e POSTGRES_DB=franklin \
  quay.io/azavea/postgis:2.5-postgres11.4-slim
```

This starts a database (`franklin-database`) in the background. If you want to see that it is still running you can use the `docker ps` command. If you want to stop it at some point you can use the command `docker stop franklin-database` and that will stop the container.

#### Running migrations

After starting the database you need to run migrations against it so that we can import data and start the web service. Migrations are a way to configure the database with the tables, extensions, and indices to ensure that the web service and importer can function properly. Running migrations will often be the first step involved any time that you wish to upgrade `franklin` as well. To run migrations use the following command:

```bash
docker run \
  --link franklin-database:franklin-database \
  quay.io/azavea/franklin:latest \
  migrate \
  --db-user benjamin \
  --db-name franklin \
  --db-password franklinsecret \
  --db-host franklin-database
```

After this command the database should have a few additional tables to store geospatial data.

#### Importing data

Racecars need fuel to run and `franklin` needs data to be really useful. The `import` command allows you to import any static STAC collections or catalogs into `franklin` to serve dynamically. The following command will need adjusting depending on where the data you want to import resides. This particular example assumes that there is a local catalog ready to be imported.

```bash
docker run \
  --link franklin-database:franklin-database \
  -v $HOME/projects/franklin/data/:/opt/data/ \
  quay.io/azavea/franklin:latest \
  import \
  --db-user benjamin \
  --db-name franklin \
  --db-password franklinsecret \
  --db-host franklin-database \
  --catalog-root /opt/data/catalog.json
```

#### Running the service

At this point you are ready to run the service. To start the service on the default port `9090` you can run the following command:

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
      - POSTGRES_URL=jdbc:postgresql://database.service.internal/
      - POSTGRES_NAME=franklin
      - POSTGRES_USER=franklin
      - POSTGRES_PASSWORD=franklin
    links:
      - database:database.service.internal
    ports:
      - "9090:9090"
```

Second, you can run `docker-compose run franklin migrate` to set up the database. Next, you can import a local dataset by copying it to the same directory as the `docker-compose.yml` file you created. Assuming that the root catalog is `catalog.json` the command would be `docker-compose run franklin import --catalog-root /opt/data/catalog.json`. Lastly, once the import is finished you can start the webserver and go to [`localhost:9090`](http://localhost:9090) to view your catalog.