package chess.model.dao

import slick.jdbc.PostgresProfile.api.*
import slick.jdbc.PostgresProfile
import scala.concurrent.Future

class GameDaoImpl(db: Database) extends GameDao:
  import GameTable.*

  override def init(): Future[Unit] =
    db.run(games.schema.createIfNotExists)

  override def insert(row: GameRow): Future[Unit] =
    db.run(games.insertOrUpdate(row)).map(_ => ())

  override def findAll(): Future[Vector[GameRow]] =
    db.run(games.sortBy(_.datePlayed.desc).result).map(_.toVector)

  override def findById(id: String): Future[Option[GameRow]] =
    db.run(games.filter(_.id === id).result.headOption)

  override def delete(id: String): Future[Unit] =
    db.run(games.filter(_.id === id).delete).map(_ => ())

  override def clear(): Future[Unit] =
    db.run(games.delete).map(_ => ())

object GameDaoImpl:
  def apply(url: String, user: String, password: String): GameDaoImpl =
    val db = Database.forURL(
      url      = url,
      user     = user,
      password = password,
      driver   = "org.postgresql.Driver"
    )
    new GameDaoImpl(db)
