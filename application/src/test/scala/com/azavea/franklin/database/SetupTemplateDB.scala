package com.azavea.franklin.database

import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import doobie._
import doobie.free.connection.unit
import doobie.implicits._
import doobie.util.transactor.Strategy
import org.flywaydb.core.Flyway
import org.specs2._
import org.specs2.specification.{AfterAll, BeforeAll}

import scala.concurrent.ExecutionContext.Implicits.global

object SetupTemplateDB {
  val templateDbName: String = "testing_template"

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  // db create/drop cannot be done with transactions
  // this transactor has error handling and cleanup but no transaction/auto-commit behavior
  val transactor: Transactor[IO] = DatabaseConfig.nonHikariTransactor[IO]("")

  // we use a template database so that migrations only need to be run once
  // test-suite specific databases are created using this db as a template
  def withoutTransaction[A](p: ConnectionIO[A]): ConnectionIO[A] =
    FC.setAutoCommit(true) *> p <* FC.setAutoCommit(false)

  // drop and create template database
  val setupDB: ConnectionIO[Int] =
    (fr"DROP DATABASE IF EXISTS" ++ Fragment.const(templateDbName) ++ fr";" ++
      fr"CREATE DATABASE" ++ Fragment.const(templateDbName)).update.run

  withoutTransaction(setupDB)
    .transact(transactor)
    .unsafeRunSync

  // run migrations using flyway
  Flyway
    .configure()
    .dataSource(
      s"${DatabaseConfig.jdbcNoDBUrl}$templateDbName",
      DatabaseConfig.dbUser,
      DatabaseConfig.dbPassword
    )
    .locations("classpath:migrations/")
    .load()
    .migrate()
}

trait TestDatabaseSpec extends Specification with BeforeAll with AfterAll {
  this: Specification =>
  SetupTemplateDB

  val dbName: String         = getClass.getSimpleName.toLowerCase
  val templateDbName: String = SetupTemplateDB.templateDbName

  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  // Transactor used by tests with rollback behavior and transactions
  def transactor: Transactor[IO] = DatabaseConfig.nonHikariTransactor[IO](dbName)

  // this transactor has error handling and cleanup but no transaction/auto-commit behavior
  val setupXant: Transactor[IO] = Transactor.strategy
    .set(
      DatabaseConfig.nonHikariTransactor[IO](""),
      Strategy.default.copy(before = unit, after = unit)
    )

  def withoutTransaction[A](p: ConnectionIO[A]): ConnectionIO[A] =
    FC.setAutoCommit(true) *> p <* FC.setAutoCommit(false)

  override def beforeAll: Unit = {
    // using the no-transaction transactor, drop the database in case it's hanging around for some reason
    // and then create the database
    val setupSql = (
      fr"DROP DATABASE IF EXISTS" ++ Fragment.const(dbName) ++ fr";CREATE DATABASE" ++ Fragment
        .const(dbName) ++ fr"WITH TEMPLATE" ++ Fragment
        .const(templateDbName)
    ).update.run

    withoutTransaction(setupSql).transact(setupXant).unsafeRunSync
    ()
  }

  override def afterAll: Unit = {
    // using the no-transaction transactor, drop the db for the test suite
    val tearDownSql = (fr"DROP DATABASE IF EXISTS" ++ Fragment.const(dbName)).update.run
    withoutTransaction(tearDownSql)
      .transact(setupXant)
      .unsafeRunSync
    ()
  }
}
