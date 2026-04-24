package chess.model.dao

import cats.effect.IO
import slick.jdbc.PostgresProfile.api.*
import scala.concurrent.Await
import scala.concurrent.duration.*

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

  def close(): Unit = db.close()

object SlickGameDao:
  def create(url: String, user: String, password: String): SlickGameDao =
    val db  = Database.forURL(url = url, user = user, password = password, driver = "org.postgresql.Driver")
    val dao = new SlickGameDao(db)
    Await.result(db.run(GameTable.games.schema.createIfNotExists), 30.seconds)
    dao
