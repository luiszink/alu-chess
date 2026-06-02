package chess.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GameScriptSpec extends AnyWordSpec with Matchers:

  "GameScript" should {

    "support map and flatMap composition" in {
      val script =
        for
          a <- GameScript.pure(2)
          b <- GameScript.pure(3)
        yield a + b

      val result = GameScript.runOn(Game.newGame)(script)
      result shouldBe Right((GameTimeline.fromGame(Game.newGame), 5))
    }

    "support eval and exec projections" in {
      val script =
        for
          _    <- GameScript.applyMoveString("e2 e4")
          game <- GameScript.currentGame
        yield game.currentPlayer

      val timelineResult = script.exec(GameTimeline.fromGame(Game.newGame))
      val valueResult = script.eval(GameTimeline.fromGame(Game.newGame))

      timelineResult shouldBe a[Right[?, ?]]
      valueResult shouldBe Right(Color.Black)

      val timeline = timelineResult.toOption.get
      timeline.states should have size 2
      timeline.index shouldBe 1
    }

    "keep undo and redo as no-ops at timeline boundaries" in {
      val start = GameTimeline.fromGame(Game.newGame)

      val undoResult = GameScript.undo.run(start)
      undoResult shouldBe Right((start, start.current))

      val withOneMove = GameScript.runOn(Game.newGame)(GameScript.applyMoveString("e2 e4"))
      withOneMove shouldBe a[Right[?, ?]]
      val latest = withOneMove.toOption.get._1

      val redoResult = GameScript.redo.run(latest)
      redoResult shouldBe Right((latest, latest.current))
    }

    "apply moves and update current game" in {
      val script =
        for
          _    <- GameScript.applyMoveString("e2 e4")
          game <- GameScript.applyMoveString("e7 e5")
        yield game

      val result = GameScript.runOn(Game.newGame)(script)
      result shouldBe a[Right[?, ?]]

      val (timeline, game) = result.toOption.get
      timeline.states should have size 3
      timeline.index shouldBe 2
      game.currentPlayer shouldBe Color.White
      game.board.cell(Position(3, 4)) shouldBe Some(Piece.Pawn(Color.White))
      game.board.cell(Position(4, 4)) shouldBe Some(Piece.Pawn(Color.Black))
    }

    "support undo and redo over timeline" in {
      val script =
        for
          _       <- GameScript.applyMoveString("e2 e4")
          _       <- GameScript.applyMoveString("e7 e5")
          undone  <- GameScript.undo
          redone  <- GameScript.redo
        yield (undone, redone)

      val result = GameScript.runOn(Game.newGame)(script)
      result shouldBe a[Right[?, ?]]

      val (_, (undone, redone)) = result.toOption.get
      undone.currentPlayer shouldBe Color.Black
      undone.board.cell(Position(3, 4)) shouldBe Some(Piece.Pawn(Color.White))
      undone.board.cell(Position(4, 4)) shouldBe None

      redone.currentPlayer shouldBe Color.White
      redone.board.cell(Position(4, 4)) shouldBe Some(Piece.Pawn(Color.Black))
    }

    "drop future states when applying a move after undo" in {
      val script =
        for
          _      <- GameScript.applyMoveString("e2 e4")
          _      <- GameScript.applyMoveString("e7 e5")
          _      <- GameScript.undo
          _      <- GameScript.applyMoveString("d7 d5")
          after  <- GameScript.timeline
          redone <- GameScript.redo
        yield (after, redone)

      val result = GameScript.runOn(Game.newGame)(script)
      result shouldBe a[Right[?, ?]]

      val (_, (after, redone)) = result.toOption.get
      after.states should have size 3
      after.isAtLatest shouldBe true
      after.current.board.cell(Position(4, 3)) shouldBe Some(Piece.Pawn(Color.Black))

      // No future left to redo: redo should be a no-op on the latest state.
      redone shouldBe after.current
    }

    "return current game for applyMoves on an empty move list" in {
      val result = GameScript.runOn(Game.newGame)(GameScript.applyMoves(Nil))
      result shouldBe Right((GameTimeline.fromGame(Game.newGame), Game.newGame))
    }

    "fail fast on invalid move parsing with applyMoveString" in {
      val script =
        for
          _ <- GameScript.applyMoveString("e2 e4")
          _ <- GameScript.applyMoveString("not-a-move")
          _ <- GameScript.applyMoveString("e7 e5")
        yield ()

      val result = GameScript.runOn(Game.newGame)(script)
      result shouldBe Left(ChessError.InvalidMoveFormat("not-a-move"))
    }

    "short-circuit flatMap when a step fails" in {
      val timeline =
        GameTimeline
          .fromGame(Game.newGame)
          .append(Game.newGame.applyMove(Move(Position(1, 4), Position(3, 4))).get)

      val script =
        GameScript
          .fail[Unit](ChessError.InvalidMoveFormat("boom"))
          .flatMap(_ => GameScript.modify(_.undo))

      val result = script.run(timeline)
      result shouldBe Left(ChessError.InvalidMoveFormat("boom"))
    }

    "propagate domain errors through the script" in {
      val script =
        for
          _ <- GameScript.applyMoveString("e2 e4")
          _ <- GameScript.applyMoveString("e2 e5")
        yield ()

      val result = GameScript.runOn(Game.newGame)(script)
      result shouldBe Left(ChessError.NoPieceAtSource(Position(1, 4)))
    }
  }