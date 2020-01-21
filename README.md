[![CircleCI](https://circleci.com/gh/azavea/franklin/tree/master.svg?style=svg)](https://circleci.com/gh/azavea/franklin/tree/master) [![Gitter chat](https://badges.gitter.im/azavea/franklin-stac.png)](https://gitter.im/franklin-stac/community) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# franklin

A [STAC]() and [OGC API Features](http://docs.opengeospatial.org/is/17-069r3/17-069r3.html) compliant web service focused on ease-of-use for end-users.

## Usage

To get started you need to have [`docker`](https://www.docker.com) installed on your system to run published `franklin` containers. To start the service you can use the following command:

_not_ _implemented_
```
docker run quay.io/azavea/franklin:latest server \
   --db-host <database-host> \
   --db-password <password> \
   --db-name <database-name>
```

This will start a [server](http://localhost:9090) without data using a provided database. If migrations need to be run they will be run on server startup.

## Features Implemented

### STAC Core
#### Capabilities
- [x] `GET /`
- [x] `GET /conformance`
- [x] `GET /collections`
- [x] `GET /collections/{collectionId}`

#### Data
- [x] `GET /collections/{collectionId}/items`
- [x] `GET /collections/{collectionId}/items/{featureId}`

#### STAC
- [x] `GET /stac`
- [x] `GET /stac/search`
- [x] `POST /stac/search`

### STAC Transaction
- [x] `POST /collections/{collectionId}/items`
- [x] `PUT /collections/{collectionId}/items/{featureId}`
- [x] `PATCH /collections/{collectionId}/items/{featureId}`
- [x] `DELETE /collections/{collectionId}/items/{featureId}`

### Other Features
- [x] Index Static Catalogs
- [ ] Raster Tile Service for Items with COGs
- [ ] Raster Tile Service for COG collections
- [ ] Vector Tile Service for Label Collections
