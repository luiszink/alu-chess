package chess.controller.perf

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import chess.model.dao.{GameDao, InMemoryGameDao, MongoGameDao, SlickGameDao}

/** Holds simultaneous references to all DAO backends that could be opened
  * successfully at startup. A backend that fails to connect is recorded as
  * "unavailable" — the corresponding REST endpoints will then return 503
  * rather than crashing the controller.
  *
  * The in-memory DAO is always available.
  */
final case class DaoRegistry(
  daos: Map[String, GameDao],
  errors: Map[String, String],
):
  val available: List[String] = daos.keys.toList.sorted
  def lookup(name: String): Option[GameDao] = daos.get(name)
  def errorFor(name: String): Option[String] = errors.get(name)

object DaoRegistry:

  /** Build a registry by attempting to open Postgres + Mongo. Failures are
    * captured and exposed via [[DaoRegistry.errors]] so the API can surface
    * them. The in-memory DAO is always added.
    */
  def make: Resource[IO, DaoRegistry] =
    val memDao: GameDao = InMemoryGameDao()

    val pgUrl  = sys.env.get("DB_URL").orElse(Some("jdbc:postgresql://localhost:5432/chess"))
    val pgUser = sys.env.getOrElse("DB_USER",     "chess")
    val pgPass = sys.env.getOrElse("DB_PASSWORD", "chess")

    val mongoUri = sys.env.get("MONGO_URI").orElse(Some("mongodb://localhost:27017"))
    val mongoDb  = sys.env.getOrElse("MONGO_DB",  "chess")

    val openPg: Resource[IO, Either[String, GameDao]] =
      Resource.make(
        IO.blocking(SlickGameDao.create(pgUrl.get, pgUser, pgPass))
          .attempt
          .map(_.left.map(_.getMessage))
      ) {
        case Right(d: SlickGameDao) => IO(d.close()).attempt.void
        case _                      => IO.unit
      }.map(_.map(identity[GameDao]))

    val openMongo: Resource[IO, Either[String, GameDao]] =
      MongoGameDao.resource(mongoUri.get, mongoDb)
        .map(d => Right(d: GameDao))
        .handleErrorWith((e: Throwable) =>
          Resource.pure[IO, Either[String, GameDao]](Left(e.getMessage))
        )

    for
      pg <- openPg
      mg <- openMongo
    yield
      val initial = Map.empty[String, GameDao]
      val errs    = Map.empty[String, String]
      val (daos1, errs1) = pg match
        case Right(d) => (initial + ("postgres" -> d), errs)
        case Left(e)  => (initial,                     errs + ("postgres" -> e))
      val (daos2, errs2) = mg match
        case Right(d) => (daos1 + ("mongo" -> d), errs1)
        case Left(e)  => (daos1,                  errs1 + ("mongo" -> e))
      val daos3 = daos2 + ("memory" -> memDao)
      DaoRegistry(daos3, errs2)
