package chess.controller

import cats.effect.*
import cats.effect.std.Queue
import io.circe.Json

case class GameEntry(
  controller: Controller,
  sseQueues:  Ref[IO, List[Queue[IO, Option[Json]]]],
)

class GameRegistry(ref: Ref[IO, Map[String, GameEntry]]):

  def create(gameId: String): IO[GameEntry] =
    for
      ctrl      <- IO(Controller())
      queuesRef <- Ref.of[IO, List[Queue[IO, Option[Json]]]](Nil)
      entry      = GameEntry(ctrl, queuesRef)
      _         <- ref.update(_ + (gameId -> entry))
    yield entry

  def get(gameId: String): IO[Option[GameEntry]] =
    ref.get.map(_.get(gameId))

  def remove(gameId: String): IO[Unit] =
    ref.update(_ - gameId)

  def list: IO[List[String]] =
    ref.get.map(_.keys.toList)

object GameRegistry:
  def make: IO[GameRegistry] =
    Ref.of[IO, Map[String, GameEntry]](Map.empty).map(new GameRegistry(_))
