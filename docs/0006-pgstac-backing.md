# 5. PGStac Backing

Date: 2022-07-15

## Status

Accepted

## Context

Prior to upcoming release 2.0.0, Franklin maintenance depended on lock-step
database migrations and code updates. This fact had a few unfortunate consequences;
perhaps most notably: increased development costs when keeping up with the
fast-moving STAC API specification, application complexity, and a lack of
attention to the details of database performance.

Recent work at Azavea with [stac-fastapi](https://github.com/stac-utils/stac-fastapi)
and [pgstac](https://github.com/stac-utils/pgstac) has provided an alternative
model of STAC API development. On this model, the application server is a
very thin layer on top of a database that comes pre-baked with the tables,
functions, and indices which optimally support stac-api operations. One
upshot of this shift in strategies is dramatically simplified database
interaction and a far smaller code footprint which requires maintenance. In
place of directly managing database behavior through a complex ORM or DAO,
pgstac enables extremely terse and readable SQL function calls which handle
all the details inside the database.


## Tile Serving

One cost of this update is the loss of Franklin's tiling support. As the database
provided by pgstac doesn't concern itself with tiles, this is not something
which can be trivially implemented. It will either require running supplementary
migrations on top of pgstac's provided database or else separating concerns around
tiling and previewing of assets from those concerns related to the stac-api
itself.

## Decision

Franklin will replace its 1.X database and related logic with pgstac. The initial
push will be a considerable effort but the long-term benefits in terms of maintainability,
consistency with the stac-api specification, amount of code, and database performance
far outweigh the upfront investment. Additionally, by leveraging the considerable
efforts behind pgstac, we have the opportunity to help settle questions of 'best
practice' related to the creation of stac services and to work within a larger community
of STAC practitioners towards a stac-api "backend" (in the form of a postgres
database) which other languages and frameworks can use to quickly implement a
stac-api server.

## Consequences

Several tasks come out of this decision. We'll need to:

- update the CHANGELOG
- rewrite the entire database project around pgstac interaction
- update relevant models with stac-api specification 1.0 changes
- modify endpoints as necessary (e.g. removing those for tiling)
- fix or ammend the deployment as necessary
- cut a 2.0 release
