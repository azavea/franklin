package com.azavea.franklin.database

import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.{Read, Write}
import doobie.{LogHandler => _, _}

import java.util.UUID

/**
  * This is abstraction over the listing of arbitrary types from the DB with filters/pagination
  */
abstract class Dao[Model: Read: Write] extends Filterables {

  val tableName: String

  /** The fragment which holds the associated table's name */
  def tableF: Fragment = Fragment.const(tableName)

  /** An abstract select statement to be used for constructing queries */
  def selectF: Fragment

  /** Begin construction of a complex, filtered query */
  def query: Dao.QueryBuilder[Model] =
    Dao.QueryBuilder[Model](selectF, tableF, List.empty)
}

object Dao {

  final case class QueryBuilder[Model: Read: Write](
      selectF: Fragment,
      tableF: Fragment,
      filters: List[Option[Fragment]],
      countFragment: Option[Fragment] = None
  ) {

    val countF: Fragment =
      countFragment.getOrElse(fr"SELECT count(id) FROM" ++ tableF)
    val deleteF: Fragment = fr"DELETE FROM" ++ tableF
    val existF: Fragment  = fr"SELECT 1 FROM" ++ tableF

    /** Add another filter to the query being constructed */
    def filter[M >: Model, T](
        thing: T
    )(implicit filterable: Filterable[M, T]): QueryBuilder[Model] =
      this.copy(filters = filters ++ filterable.toFilters(thing))

    def filter[M >: Model](
        thing: Fragment
    )(implicit filterable: Filterable[M, Fragment]): QueryBuilder[Model] =
      thing match {
        case Fragment.empty => this
        case _              => this.copy(filters = filters ++ filterable.toFilters(thing))
      }

    def filter[M >: Model](id: UUID)(
        implicit filterable: Filterable[M, Option[Fragment]]
    ): QueryBuilder[Model] = {
      this.copy(filters = filters ++ filterable.toFilters(Some(fr"id = ${id}")))
    }

    def filter[M >: Model](
        fragments: List[Option[Fragment]]
    ): QueryBuilder[Model] = {
      this.copy(filters = filters ::: fragments)
    }

    def listQ(limit: Int): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"LIMIT $limit")
        .query[Model]

    /** Provide a list of responses */
    def list(limit: Int): ConnectionIO[List[Model]] = {
      listQ(limit).to[List]
    }

    def listQ(offset: Int, limit: Int): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ fr"OFFSET $offset" ++ fr"LIMIT $limit")
        .query[Model]

    def listQ(offset: Int, limit: Int, orderClause: Fragment): Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*) ++ orderClause ++ fr"OFFSET $offset" ++ fr"LIMIT $limit")
        .query[Model]

    /** Provide a list of responses */
    def list: ConnectionIO[List[Model]] = {
      (selectF ++ Fragments.whereAndOpt(filters: _*))
        .query[Model]
        .to[List]
    }

    def count: ConnectionIO[Int] = (countF ++ Fragments.whereAndOpt(filters: _*)).query[Int].unique

    /** Provide a list of responses */
    def list(offset: Int, limit: Int): ConnectionIO[List[Model]] = {
      listQ(offset, limit).to[List]
    }

    def list(offset: Int, limit: Int, orderClause: Fragment): ConnectionIO[List[Model]] = {
      listQ(offset, limit, orderClause).to[List]
    }

    def list(page: Page): ConnectionIO[List[Model]] = {
      listQ(page.offset, page.limit.getOrElse(20)).to[List]
    }

    def selectQ: Query0[Model] =
      (selectF ++ Fragments.whereAndOpt(filters: _*)).query[Model]

    /** Select a single value - returning an Optional value */
    def selectOption: ConnectionIO[Option[Model]] =
      selectQ.option

    /** Select a single value - throw on failure */
    def select: ConnectionIO[Model] = {
      selectQ.unique
    }

    def deleteQOption: Option[Update0] = {
      if (filters.isEmpty) {
        None
      } else {
        Some((deleteF ++ Fragments.whereAndOpt(filters: _*)).update)
      }
    }

    def delete: ConnectionIO[Int] = {
      deleteQOption
        .getOrElse(
          throw new Exception("Unsafe delete - delete requires filters")
        )
        .run
    }

    def exists: ConnectionIO[Boolean] = {
      (existF ++ Fragments.whereAndOpt(filters: _*) ++ fr"LIMIT 1")
        .query[Int]
        .to[List]
        .map(_.nonEmpty)
    }
  }
}
