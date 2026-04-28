package chess.model.benchmark

import cats.effect.{IO, Resource}
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.operations.{Filter, Index}
import mongo4cats.circe.given

class MongoBenchmarkRunDao(collection: MongoCollection[IO, BenchmarkResult])
    extends BenchmarkRunDao:

  override def init(): IO[Unit] =
    collection.createIndex(Index.ascending("id")).void

  override def insert(result: BenchmarkResult): IO[Unit] =
    collection
      .replaceOne(
        Filter.eq("id", result.id),
        result,
        com.mongodb.client.model.ReplaceOptions().upsert(true),
      )
      .void

  override def findAll(limit: Int): IO[Vector[BenchmarkResult]] =
    collection.find.sortByDesc("startedAt").limit(limit).all.map(_.toVector)

  override def findById(id: String): IO[Option[BenchmarkResult]] =
    collection.find(Filter.eq("id", id)).first

  override def clear(): IO[Unit] =
    collection.drop.void

object MongoBenchmarkRunDao:
  def resource(uri: String, dbName: String): Resource[IO, MongoBenchmarkRunDao] =
    MongoClient.fromConnectionString[IO](uri).evalMap { client =>
      for
        db   <- client.getDatabase(dbName)
        coll <- db.getCollectionWithCodec[BenchmarkResult]("benchmark_runs")
        dao   = new MongoBenchmarkRunDao(coll)
        _    <- dao.init()
      yield dao
    }
