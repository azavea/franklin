[![CircleCI](https://circleci.com/gh/azavea/franklin/tree/master.svg?style=svg)](https://circleci.com/gh/azavea/franklin/tree/master)

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
- [ ] `GET /stac`
- [ ] `GET /stac/search`
- [ ] `POST /stac/search`

### STAC Transaction
- [ ] `POST /collections/{collectionId}/items`
- [ ] `PUT /collections/{collectionId}/items/{featureId}`
- [ ] `PATCH /collections/{collectionId}/items/{featureId}`
- [ ] `DELETE /collections/{collectionId}/items/{featureId}`

### Other Features
- [ ] Index Static Catalogs
- [ ] Raster Tile Service for COG collections
- [ ] Vector Tile Service for Label Collections
