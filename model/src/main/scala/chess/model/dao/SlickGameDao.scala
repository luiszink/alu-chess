package chess.model.dao

import cats.effect.{IO, Resource}
import slick.jdbc.PostgresProfile.api.*

class SlickGameDao(db: Database) extends GameDao:
  import GameTable.*

  override def init(): IO[Unit] =
    IO.fromFuture(IO.delay(db.run(games.schema.createIfNotExists)))

  override def insert(row: GameRow): IO[Unit] =
    IO.fromFuture(IO.delay(db.run(games.insertOrUpdate(row)))).void

  override def findAll(): IO[Vector[GameRow]] =
    IO.fromFuture(IO.delay(db.run(games.sortBy(_.datePlayed.desc).result)))
      .map(_.toVector)

  override def findById(id: String): IO[Option[GameRow]] =
    IO.fromFuture(IO.delay(db.run(games.filter(_.id === id).result.headOption)))

  override def delete(id: String): IO[Unit] =
    IO.fromFuture(IO.delay(db.run(games.filter(_.id === id).delete))).void

  override def clear(): IO[Unit] =
    IO.fromFuture(IO.delay(db.run(games.delete))).void

object SlickGameDao:
  def resource(url: String, user: String, password: String): Resource[IO, SlickGameDao] =
    Resource
      .make(IO.delay(Database.forURL(url, user, password, driver = "org.postgresql.Driver")))(db => IO.delay(db.close()))
      .evalMap { db =>
        val dao = new SlickGameDao(db)
        dao.init().as(dao)
      }
