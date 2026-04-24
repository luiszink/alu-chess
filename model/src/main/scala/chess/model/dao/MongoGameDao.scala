package chess.model.dao

import cats.effect.{IO, Resource}
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.operations.{Filter, Index}
import mongo4cats.circe.*

class MongoGameDao(collection: MongoCollection[IO, GameRow]) extends GameDao:

  override def init(): IO[Unit] =
    collection.createIndex(Index.ascending("id")).void

  override def insert(row: GameRow): IO[Unit] =
    collection
      .replaceOne(Filter.eq("id", row.id), row, com.mongodb.client.model.ReplaceOptions().upsert(true))
      .void

  override def findAll(): IO[Vector[GameRow]] =
    collection.find.sortByDesc("datePlayed").all.map(_.toVector)

  override def findById(id: String): IO[Option[GameRow]] =
    collection.find(Filter.eq("id", id)).first

  override def delete(id: String): IO[Unit] =
    collection.deleteOne(Filter.eq("id", id)).void

  override def clear(): IO[Unit] =
    collection.drop.void

object MongoGameDao:
  def resource(uri: String, dbName: String): Resource[IO, MongoGameDao] =
    MongoClient.fromConnectionString[IO](uri).evalMap { client =>
      for
        db         <- client.getDatabase(dbName)
        collection <- db.getCollectionWithCirce[GameRow]("games")
        dao         = new MongoGameDao(collection)
        _          <- dao.init()
      yield dao
    }
