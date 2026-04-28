package chess.model.dao

import cats.effect.IO
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

/** In-memory `GameDao` implementation, primarily used as a baseline for the
  * DAO benchmark suite (chess.model.benchmark) and for tests. Thread-safe
  * via `ConcurrentHashMap` so concurrent benchmark iterations don't corrupt
  * state.
  */
class InMemoryGameDao extends GameDao:
  private val store: ConcurrentHashMap[String, GameRow] =
    new ConcurrentHashMap[String, GameRow]()

  override def init(): IO[Unit] = IO.unit

  override def insert(row: GameRow): IO[Unit] =
    IO(store.put(row.id, row)).void

  override def findAll(): IO[Vector[GameRow]] =
    IO {
      store.values.iterator.asScala.toVector
        .sortBy(_.datePlayed)(Ordering[String].reverse)
    }

  override def findById(id: String): IO[Option[GameRow]] =
    IO(Option(store.get(id)))

  override def delete(id: String): IO[Unit] =
    IO(store.remove(id)).void

  override def clear(): IO[Unit] =
    IO(store.clear())

object InMemoryGameDao:
  def apply(): InMemoryGameDao = new InMemoryGameDao()
