package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class PgnSpec extends AnyWordSpec with Matchers {

  "Pgn.toPgn" should {

    "export an empty game" in {
      val pgn = Pgn.toPgn(Game.newGame)
      pgn should include("[Event")
      pgn should include("[White")
      pgn should include("[Black")
      pgn should include("[Result \"*\"]")
      pgn should endWith("*")
    }

    "export a game with moves" in {
      val game = Game.newGame
        .applyMove(Move(Position(1, 4), Position(3, 4))).get // e4
        .applyMove(Move(Position(6, 4), Position(4, 4))).get // e5
      val pgn = Pgn.toPgn(game)
      pgn should include("1. e4 e5")
      pgn should include("[Result \"*\"]")
    }

    "include time control header when provided" in {
      val pgn = Pgn.toPgn(Game.newGame, timeControl = Some(TimeControl.Blitz5_0))
      pgn should include("[TimeControl \"300+0\"]")
    }

    "include time control with increment" in {
      val pgn = Pgn.toPgn(Game.newGame, timeControl = Some(TimeControl.Blitz3_2))
      pgn should include("[TimeControl \"180+2\"]")
    }

    "show correct result for checkmate" in {
      // Fool's Mate
      val game = Game.newGame
        .applyMove(Move(Position(1, 5), Position(2, 5))).get // f3
        .applyMove(Move(Position(6, 4), Position(4, 4))).get // e5
        .applyMove(Move(Position(1, 6), Position(3, 6))).get // g4
        .applyMove(Move(Position(7, 3), Position(3, 7))).get // Qh4#
      game.status shouldBe GameStatus.Checkmate
      val pgn = Pgn.toPgn(game)
      pgn should include("[Result \"0-1\"]")
      pgn should include("Qh4#")
      pgn should endWith("0-1")
    }

    "show correct result for stalemate" in {
      val board = Board.empty
        .put(Position(6, 5), Piece.King(Color.White))
        .put(Position(4, 6), Piece.Queen(Color.White))
        .put(Position(7, 7), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
        .applyMove(Move(Position(4, 6), Position(5, 6))).get // Qg6 -> stalemate
      game.status shouldBe GameStatus.Stalemate
      val pgn = Pgn.toPgn(game)
      pgn should include("[Result \"1/2-1/2\"]")
      pgn should endWith("1/2-1/2")
    }

    "show correct result for resignation by black" in {
      // Black resigns -> white wins 1-0
      val game = Game.newGame
        .applyMove(Move(Position(1, 4), Position(3, 4))).get // e4
        .resign // black resigns
      game.currentPlayer shouldBe Color.Black
      val pgn = Pgn.toPgn(game)
      // Resigned: currentPlayer = Black (who resigned), so opposite = White wins
      pgn should include("[Result \"1-0\"]")
    }

    "show correct result for timeout" in {
      val game = Game.newGame.copy(status = GameStatus.TimeOut)
      val pgn = Pgn.toPgn(game)
      pgn should include("[Result \"0-1\"]")
    }

    "show correct result for timeout where black times out" in {
      val game = Game.newGame
        .applyMove(Move(Position(1, 4), Position(3, 4))).get // e4
        .copy(status = GameStatus.TimeOut) // black timed out
      val pgn = Pgn.toPgn(game)
      pgn should include("[Result \"1-0\"]") // current player is black (who timed out) -> white wins
    }

    "show correct result for checkmate where white wins" in {
      // Scholar's mate position - black is checkmated, white wins
      val board = Board.empty
        .put(Position(6, 0), Piece.Rook(Color.White))
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(7, 6), Piece.King(Color.Black))
        .put(Position(6, 5), Piece.Pawn(Color.Black))
        .put(Position(6, 6), Piece.Pawn(Color.Black))
        .put(Position(6, 7), Piece.Pawn(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
        .applyMove(Move(Position(6, 0), Position(7, 0))).get // Ra8#
      game.status shouldBe GameStatus.Checkmate
      val pgn = Pgn.toPgn(game)
      pgn should include("[Result \"1-0\"]")
    }

    "show correct result for draw" in {
      val game = Game.newGame.copy(status = GameStatus.Draw)
      val pgn = Pgn.toPgn(game)
      pgn should include("[Result \"1/2-1/2\"]")
    }

    "use custom player names" in {
      val pgn = Pgn.toPgn(Game.newGame, white = "Alice", black = "Bob")
      pgn should include("[White \"Alice\"]")
      pgn should include("[Black \"Bob\"]")
    }

    "export game with odd number of moves" in {
      val game = Game.newGame
        .applyMove(Move(Position(1, 4), Position(3, 4))).get // e4
      val pgn = Pgn.toPgn(game)
      pgn should include("1. e4")
    }

    "show correct result when black delivers checkmate" in {
      // Fool's mate: black wins
      val game = Game.newGame
        .applyMove(Move(Position(1, 5), Position(2, 5))).get
        .applyMove(Move(Position(6, 4), Position(4, 4))).get
        .applyMove(Move(Position(1, 6), Position(3, 6))).get
        .applyMove(Move(Position(7, 3), Position(3, 7))).get
      val pgn = Pgn.toPgn(game)
      // Black (current player's opposite) wins
      pgn should include("0-1")
    }
  }

  "Pgn parser delegation" should {

    val malformedTagPgn = "[Event Test]\n\n1. e4 *"

    "delegate parseGameE to the active parser" in {
      val originalParser = Pgn.activeParser
      try {
        Pgn.activeParser = PgnParserType.Combinator
        Pgn.parseGameE(malformedTagPgn) shouldBe a[Left[?, ?]]

        Pgn.activeParser = PgnParserType.Fast
        val parsed = Pgn.parseGameE(malformedTagPgn)
        parsed shouldBe a[Right[?, ?]]
        parsed.toOption.get.moves should contain("e4")
      } finally {
        Pgn.activeParser = originalParser
      }
    }

    "delegate parseGame to the active parser" in {
      val originalParser = Pgn.activeParser
      try {
        Pgn.activeParser = PgnParserType.Combinator
        Pgn.parseGame(malformedTagPgn) shouldBe None

        Pgn.activeParser = PgnParserType.Fast
        Pgn.parseGame(malformedTagPgn).get.moves should contain("e4")
      } finally {
        Pgn.activeParser = originalParser
      }
    }

    "delegate replayE to the active parser" in {
      val originalParser = Pgn.activeParser
      try {
        Pgn.activeParser = PgnParserType.Combinator
        Pgn.replayE(malformedTagPgn) shouldBe a[Left[?, ?]]

        Pgn.activeParser = PgnParserType.Fast
        val replayed = Pgn.replayE(malformedTagPgn)
        replayed shouldBe a[Right[?, ?]]
        replayed.toOption.get.moveHistory should have size 1
      } finally {
        Pgn.activeParser = originalParser
      }
    }
  }

  "Pgn.parseSAN" should {

    val game = Game.newGame

    "parse a pawn move" in {
      Pgn.parseSAN("e4", game) shouldBe Some(Move(Position(1, 4), Position(3, 4)))
    }

    "parse a two-token pawn move" in {
      Pgn.parseSAN("e3", game) shouldBe Some(Move(Position(1, 4), Position(2, 4)))
    }

    "parse a knight move" in {
      Pgn.parseSAN("Nf3", game) shouldBe Some(Move(Position(0, 6), Position(2, 5)))
    }

    "parse king-side castling" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 7), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = Pgn.parseSAN("O-O", g)
      move shouldBe defined
      move.get.from shouldBe Position(0, 4)
      move.get.to shouldBe Position(0, 6)
    }

    "parse queen-side castling" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = Pgn.parseSAN("O-O-O", g)
      move shouldBe defined
      move.get.from shouldBe Position(0, 4)
      move.get.to shouldBe Position(0, 2)
    }

    "parse castling with zeros (0-0)" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 7), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      Pgn.parseSAN("0-0", g) shouldBe defined
    }

    "parse castling with zeros (0-0-0)" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      Pgn.parseSAN("0-0-0", g) shouldBe defined
    }

    "parse black king-side castling" in {
      val board = Board.empty
        .put(Position(7, 4), Piece.King(Color.Black))
        .put(Position(7, 7), Piece.Rook(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
      val g = Game(board, Color.Black, GameStatus.Playing)
      val move = Pgn.parseSAN("O-O", g)
      move shouldBe defined
      move.get.from shouldBe Position(7, 4)
      move.get.to shouldBe Position(7, 6)
    }

    "parse black queen-side castling" in {
      val board = Board.empty
        .put(Position(7, 4), Piece.King(Color.Black))
        .put(Position(7, 0), Piece.Rook(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
      val g = Game(board, Color.Black, GameStatus.Playing)
      val move = Pgn.parseSAN("O-O-O", g)
      move shouldBe defined
      move.get.from shouldBe Position(7, 4)
      move.get.to shouldBe Position(7, 2)
    }

    "parse pawn capture" in {
      val board = Board.empty
        .put(Position(3, 4), Piece.Pawn(Color.White))
        .put(Position(4, 3), Piece.Pawn(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = Pgn.parseSAN("exd5", g)
      move shouldBe defined
      move.get.from shouldBe Position(3, 4)
      move.get.to shouldBe Position(4, 3)
    }

    "parse move with check suffix" in {
      Pgn.parseSAN("e4+", game) shouldBe Some(Move(Position(1, 4), Position(3, 4)))
    }

    "parse move with checkmate suffix" in {
      Pgn.parseSAN("e4#", game) shouldBe Some(Move(Position(1, 4), Position(3, 4)))
    }

    "parse piece move with capture" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(7, 0), Piece.Knight(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = Pgn.parseSAN("Rxa8", g)
      move shouldBe defined
      move.get.from shouldBe Position(0, 0)
      move.get.to shouldBe Position(7, 0)
    }

    "parse promotion" in {
      val board = Board.empty
        .put(Position(6, 0), Piece.Pawn(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = Pgn.parseSAN("a8=Q", g)
      move shouldBe defined
      move.get.promotion shouldBe Some('Q')
    }

    "parse disambiguation by file" in {
      val board = Board.empty
        .put(Position(0, 1), Piece.Knight(Color.White))
        .put(Position(0, 5), Piece.Knight(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = Pgn.parseSAN("Nbd2", g)
      move shouldBe defined
      move.get.from shouldBe Position(0, 1)
    }

    "parse disambiguation by rank" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(7, 0), Piece.Rook(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = Pgn.parseSAN("R1a4", g)
      move shouldBe defined
      move.get.from shouldBe Position(0, 0)
    }

    "return None for invalid SAN" in {
      Pgn.parseSAN("Z9", game) shouldBe None
    }

    "return None for too-short piece move" in {
      Pgn.parseSAN("N", game) shouldBe None
    }

    "return None for too-short pawn move" in {
      Pgn.parseSAN("e", game) shouldBe None
    }

    "parse pawn capture via 'x' in original SAN" in {
      // After 1.e4 d5, white captures exd5
      val g = Game.newGame
        .applyMove(Move(Position(1, 4), Position(3, 4))).get // e4
        .applyMove(Move(Position(6, 3), Position(4, 3))).get // d5
      val move = Pgn.parseSAN("exd5", g)
      move shouldBe defined
      move.get.from shouldBe Position(3, 4) // e4
      move.get.to shouldBe Position(4, 3) // d5
    }

    "parse pawn capture with short notation (file hint from originalStr)" in {
      // Non-standard SAN where 'x' is embedded and str.length == 2
      // after removing 'x', creating the else-if branch in parsePawnMove
      val board = Board.empty
        .put(Position(3, 3), Piece.Pawn(Color.White)) // d4
        .put(Position(4, 4), Piece.Pawn(Color.Black)) // e5
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      // Non-standard SAN with embedded 'x' where str.length == 2 after removal,
      // testing the file-hint-from-originalStr branch in parsePawnMove
      val move = Pgn.parseSAN("dx5", g)
      move shouldBe defined
    }

    "parse disambiguation by full square" in {
      val board = Board.empty
        .put(Position(0, 1), Piece.Knight(Color.White))
        .put(Position(2, 1), Piece.Knight(Color.White))
        .put(Position(2, 5), Piece.Knight(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = Pgn.parseSAN("Nb1d2", g)
      move shouldBe defined
      move.get.from shouldBe Position(0, 1)
    }
  }

  "Pgn.replayPgn" should {

    "replay a simple game" in {
      val pgn = "[Event \"Test\"]\n\n1. e4 e5 2. Nf3 *"
      val result = Pgn.replayPgn(pgn)
      result shouldBe a[Right[?, ?]]
      result.map(_.moveHistory.size) shouldBe Right(3)
    }

    "replay Fool's Mate" in {
      val pgn = "1. f3 e5 2. g4 Qh4# 0-1"
      val result = Pgn.replayPgn(pgn)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Checkmate)
    }

    "return Left for invalid move" in {
      val pgn = "1. e4 Zz9 *"
      val result = Pgn.replayPgn(pgn)
      result shouldBe a[Left[?, ?]]
    }

    "handle PGN with headers" in {
      val pgn =
        """[Event "Test"]
          |[Site "Local"]
          |[Date "2025.01.01"]
          |[White "Alice"]
          |[Black "Bob"]
          |[Result "1-0"]
          |
          |1. e4 e5 1-0""".stripMargin
      val result = Pgn.replayPgn(pgn)
      result shouldBe a[Right[?, ?]]
    }

    "handle PGN with comments and annotations" in {
      val pgn = "1. e4 {best move} e5 (1...c5) 2. Nf3 *"
      val result = Pgn.replayPgn(pgn)
      result shouldBe a[Right[?, ?]]
    }

    "stop replaying after terminal status" in {
      // Fool's mate followed by extra moves that shouldn't be applied
      val pgn = "1. f3 e5 2. g4 Qh4# 3. e4 0-1"
      val result = Pgn.replayPgn(pgn)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Checkmate)
    }

    "return Left when SAN move is illegal" in {
      // e4 e4 - second e4 is illegal
      val pgn = "1. e4 e4 *"
      val result = Pgn.replayPgn(pgn)
      result shouldBe a[Left[?, ?]]
    }

    "replay empty PGN" in {
      val pgn = "*"
      val result = Pgn.replayPgn(pgn)
      result shouldBe a[Right[?, ?]]
      result.map(_.moveHistory.size) shouldBe Right(0)
    }

    "propagate Left through fold" in {
      // e4 followed by an invalid SAN, then another valid move
      // The Left should propagate through the fold
      val pgn = "1. e4 Zz9 2. d4 *"
      val result = Pgn.replayPgn(pgn)
      result shouldBe a[Left[?, ?]]
    }

    "map replayE errors to their message" in {
      val pgn = "1. e4 Zz9 *"
      Pgn.replayPgn(pgn) shouldBe Pgn.replayE(pgn).left.map(_.message)
    }
  }
}
