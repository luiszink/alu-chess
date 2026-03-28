package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class GameRecordSpec extends AnyWordSpec with Matchers {

  "GameRecord.create" should {

    "set result to 0-1 when White is checkmated (Black wins)" in {
      val game0 = Game.newGame
      // Fool's Mate: 1.f3 e5 2.g4 Qh4#
      val game1 = game0.applyMove(Move(Position(1, 5), Position(2, 5))).get  // f2-f3
      val game2 = game1.applyMove(Move(Position(6, 4), Position(4, 4))).get  // e7-e5
      val game3 = game2.applyMove(Move(Position(1, 6), Position(3, 6))).get  // g2-g4
      val game4 = game3.applyMove(Move(Position(7, 3), Position(3, 7))).get  // Qd8-h4#
      game4.status shouldBe GameStatus.Checkmate
      val states = Vector(game0, game1, game2, game3, game4)
      val record = GameRecord.create(states, None)
      record.result shouldBe "0-1"
      record.moveCount shouldBe 4
      record.timeControl shouldBe None
      record.gameStates shouldBe states
      record.id should not be empty
      record.pgn should not be empty
    }

    "set result to ½-½ for a Stalemate" in {
      val game = Game(Board.initial, Color.Black, GameStatus.Stalemate, Set.empty)
      val states = Vector(Game.newGame, game)
      val record = GameRecord.create(states, None)
      record.result shouldBe "½-½"
    }

    "set result to * for an ongoing game" in {
      val states = Vector(Game.newGame)
      val record = GameRecord.create(states, None)
      record.result shouldBe "*"
    }

    "include timeControl when provided" in {
      val tc = TimeControl(300000L, 0L, "Blitz")
      val states = Vector(Game.newGame)
      val record = GameRecord.create(states, Some(tc))
      record.timeControl shouldBe Some(tc)
    }

    "set result to 1-0 when Black is checkmated (White wins)" in {
      // Black is mated → currentPlayer is Black → else branch → "1-0"
      val game = Game(Board.initial, Color.Black, GameStatus.Checkmate, Set.empty)
      val states = Vector(Game.newGame, game)
      val record = GameRecord.create(states, None)
      record.result shouldBe "1-0"
    }

    "set result to 0-1 for TimeOut when White's turn (Black wins)" in {
      val game = Game(Board.initial, Color.White, GameStatus.TimeOut, Set.empty)
      val states = Vector(Game.newGame, game)
      val record = GameRecord.create(states, None)
      record.result shouldBe "0-1"
    }

    "set result to 1-0 for TimeOut when Black's turn (White wins)" in {
      val game = Game(Board.initial, Color.Black, GameStatus.TimeOut, Set.empty)
      val states = Vector(Game.newGame, game)
      val record = GameRecord.create(states, None)
      record.result shouldBe "1-0"
    }

    "set result to 0-1 for Resigned when White's turn (Black wins)" in {
      val game = Game(Board.initial, Color.White, GameStatus.Resigned, Set.empty)
      val states = Vector(Game.newGame, game)
      val record = GameRecord.create(states, None)
      record.result shouldBe "0-1"
    }

    "set result to 1-0 for Resigned when Black's turn (White wins)" in {
      val game = Game(Board.initial, Color.Black, GameStatus.Resigned, Set.empty)
      val states = Vector(Game.newGame, game)
      val record = GameRecord.create(states, None)
      record.result shouldBe "1-0"
    }

    "set result to ½-½ for a Draw" in {
      val game = Game(Board.initial, Color.White, GameStatus.Draw, Set.empty)
      val states = Vector(Game.newGame, game)
      val record = GameRecord.create(states, None)
      record.result shouldBe "½-½"
    }
  }
}
