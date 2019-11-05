# `franklin`

## What's `franklin`?

A [STAC](https://github.com/radiantearth/stac-spec) and [OGC API Features](http://docs.opengeospatial.org/is/17-069r3/17-069r3.html) compliant web service focused on ease-of-use for end-users.

`franklin` imports and serves STAC catalogs backed by Postgres. Its goal is to enable import and querying of STAC catalogs in a single statement.

## How do I get it?

### With a Postgres database that already exists

A runnable version of `franklin` is published as a docker container. You can run it with:

```bash
$ docker run quay.io/azavea/franklin:latest server \
   --db-host <database-host> \
   --db-password <password> \
   --db-name <database-name>
```

### Standalone

To bring up a standalone version to explore a STAC catalog locally, you can, with `docker-compose`,
`docker-compose up` with this `docker-compose.yml`:

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

  api:
    image: quay.io/azavea/franklin:latest
    depends_on:
      database:
        condition: service_healthy
	command:
	  - server
    environment:
      - AWS_PROFILE
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

You can then view api documentation for `franklin` at `:9090/api/docs.yaml`.
