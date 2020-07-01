# Development

`Franklin` is written in `scala` and relies heavily on [`stac4s`](https://github.com/azavea/stac4s/), [`geotrellis-server`](https://github.com/geotrellis/geotrellis-server), and [`GeoTrellis`](https://github.com/locationtech/geotrellis).

To get started with development you will need `scala` and `sbt` installed, though we suggest using [`bloop`](https://scalacenter.github.io/bloop/) for most development needs. To manage your versions of `java` we suggest installing [`jabba`](https://github.com/shyiko/jabba).

There is _some_ [`TypeScript`] in the project for the `html` representations of the API and you will need [`nvm`](https://github.com/nvm-sh/nvm) installed in order to manage your versions of `nodejs` for this project. The `html` pages themselves are rendered using [`twirl`](https://github.com/playframework/twirl).

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

If primarily working on the backend you will want to stop the server with `ctrl-c`. To test if your changes compile you can run `bloop compile application` or try to restart the server `./scripts/server` which will attempt to compile your changes before restarting. If your changes cause an issue with the compiled `twirl` templates you will need to edit the templates. After editing the `templates` you need to either run `./scripts/update` or run a single `sbt` command `twirlCompileTemplates`.

## Frontend Work

There are two parts to working on the frontend: `twirl` templates and `TypeScript`.

### Twirl

`twirl` is the template engine for the rendered `html`. Unfortunately, `bloop` does not compile these templates because that is handled via an `sbt` plugin. This means that in order to check if your templates compile you need to run `twirlCompileTemplates` in `sbt` or run `./scripts/update`. We suggest keeping an `sbt` shell open while actively working so you can quickly check if your changes compile.

### TypeScript

Bits and pieces of `typescript` are necessary for interactive elements on the frontend - mostly the map interactions. If you are making changes to this code you need to compile your `TypeScript`. This can be done either by running `./scripts/update` or manually running `npm run build`. This will compile the `TypeScript` files in `frontend/` and place them in the `assets` directory of the application. This will be loaded by the frontend on refresh.
