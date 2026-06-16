package chess.controller

import cats.effect.IO

trait AiMoveRequester:
  def requestMove(gameId: String, controller: ControllerInterface): IO[Unit]

object AiMoveRequester:
  val noop: AiMoveRequester = (_: String, _: ControllerInterface) => IO.unit
