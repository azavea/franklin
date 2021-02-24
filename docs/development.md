# Development

`Franklin` is written in `scala` and relies heavily on [`stac4s`](https://github.com/azavea/stac4s/), [`geotrellis-server`](https://github.com/geotrellis/geotrellis-server), and [`GeoTrellis`](https://github.com/locationtech/geotrellis).

To get started with development you will need `scala` and `sbt` installed, though we suggest using [`bloop`](https://scalacenter.github.io/bloop/) for most development needs. To manage your versions of `java` we suggest installing [`jabba`](https://github.com/shyiko/jabba).


This project uses ["Scripts to Rule Them All"](https://github.blog/2015-06-30-scripts-to-rule-them-all/) (STRTA) to manage most of the development process.

Additionally, this project uses [`docker-compose`] to bring up a development database. For this you will need port `5432` open.

## Getting Started

In the root of the project set the `nodejs` and `java` versions with the following commands:

```
jabba use
nvm use
```

After that, run `./scripts/setup` - this will set up the project, install dependencies, and run migrations against your development database.

At this point you can run `./scripts/server` and either navigate to [`localhost:9090`](http://localhost:9090) in your browser or make a request using `cURL`.

## Backend Work

If primarily working on the backend you will want to stop the server with `ctrl-c`. To test if your changes compile you can run `bloop compile application` or try to restart the server `./scripts/server` which will attempt to compile your changes before restarting.

## Frontend Work

Frontend work is 