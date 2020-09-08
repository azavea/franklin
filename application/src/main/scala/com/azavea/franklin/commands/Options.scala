package com.azavea.franklin.api.commands

import cats.data._
import cats.effect._
import com.azavea.franklin.crawler._
import com.monovore.decline.Opts

object Options extends DatabaseOptions with ApiOptions {

  private val itemsFromCli: Opts[NonEmptyList[String]] = Opts.arguments[String]("items")

  private def itemsFromFile[F[_]: Sync]: Opts[NonEmptyList[String]] =
    Opts
      .option[String]("file", "file containing URIs to STAC items to import", short = "f")
      .mapValidated { s =>
        StacIO
          .readLinesFromPath(s)
          .map { l =>
            NonEmptyList.fromList(l) match {
              case Some(nel) => Validated.valid(nel)
              case _         => Validated.invalidNel(s"Could not read item URIs from file: $s")
            }
          }
          .unsafeRunSync()
      }

  def stacItems[F[_]: Sync]: Opts[NonEmptyList[String]] = itemsFromCli orElse itemsFromFile

  def collectionID: Opts[String] = Opts.argument[String]("collection-id")

  val catalogRoot: Opts[String] = Opts
    .option[String]("catalog-root", "Root of STAC catalog to import")

  val dryRun: Opts[Boolean] =
    Opts.flag("dry-run", help = "Walk a catalog, but don't commit records to the database").orFalse
}
