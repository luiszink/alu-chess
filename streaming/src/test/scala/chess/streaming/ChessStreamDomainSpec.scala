package chess.streaming

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import chess.model.{Board, Color, Game, GameStatus, Move, Piece, Position}

class ChessStreamDomainSpec extends AnyWordSpec with Matchers:

  // ── ParsedMove.toMove ─────────────────────────────────────────────────────

  "ParsedMove.toMove" should {

    "return Some(Move) for valid algebraic positions" in {
      ParsedMove("e2", "e4", None).toMove shouldBe defined
    }

    "return None for an invalid from-position" in {
      ParsedMove("z9", "e4", None).toMove shouldBe None
    }

    "return None for an invalid to-position" in {
      ParsedMove("e2", "x8", None).toMove shouldBe None
    }

    "return None when both positions are invalid" in {
      ParsedMove("xx", "yy", None).toMove shouldBe None
    }

    "include the promotion char in the Move" in {
      val m = ParsedMove("e7", "e8", Some('Q')).toMove
      m shouldBe defined
      m.get.promotion shouldBe Some('Q')
    }

    "produce None promotion when promo is absent" in {
      val m = ParsedMove("e2", "e4", None).toMove
      m shouldBe defined
      m.get.promotion shouldBe None
    }
  }

  // ── GameEvent.isCapture ───────────────────────────────────────────────────

  "GameEvent.isCapture" should {

    "return true when the target square held an opponent piece" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(4, 0), Piece.Rook(Color.White))
        .put(Position(4, 7), Piece.Knight(Color.Black))  // at h5
        .put(Position(7, 4), Piece.King(Color.Black))
      val game  = Game(board, Color.White, GameStatus.Playing)
      val after = game.applyMove(Move(Position(4, 0), Position(4, 7))).get
      val ev    = GameEvent(1, ParsedMove("a5", "h5", None), game, after)
      ev.isCapture shouldBe true
    }

    "return false for a quiet pawn move" in {
      val game  = Game.newGame
      val after = game.applyMove(Move(Position(1, 4), Position(3, 4))).get
      val ev    = GameEvent(1, ParsedMove("e2", "e4", None), game, after)
      ev.isCapture shouldBe false
    }

    "return true for en passant (pawn changes file with empty target square)" in {
      val board = Board.empty
        .put(Position(4, 4), Piece.Pawn(Color.White))   // e5
        .put(Position(4, 3), Piece.Pawn(Color.Black))   // d5
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val lastMove = Some(Move(Position(6, 3), Position(4, 3)))  // d7-d5 double push
      val game     = Game(board, Color.White, GameStatus.Playing, lastMove = lastMove)
      val after    = game.applyMove(Move(Position(4, 4), Position(5, 3))).get  // e5xd6 e.p.
      val ev       = GameEvent(1, ParsedMove("e5", "d6", None), game, after)
      ev.isCapture shouldBe true
    }
  }

  // ── GameEvent.isCheck ─────────────────────────────────────────────────────

  "GameEvent.isCheck" should {

    "return true when gameAfter has status Check" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(1, 0), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game  = Game(board, Color.White, GameStatus.Playing)
      val after = game.applyMove(Move(Position(1, 0), Position(7, 0))).get  // Ra2-Ra8+
      val ev    = GameEvent(1, ParsedMove("a2", "a8", None), game, after)
      ev.isCheck shouldBe true
    }

    "return true when gameAfter has status Checkmate" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(6, 0), Piece.Rook(Color.White))   // a7 → will move to a8#
        .put(Position(7, 6), Piece.King(Color.Black))   // g8
        .put(Position(6, 5), Piece.Pawn(Color.Black))   // f7 — blocks escape
        .put(Position(6, 6), Piece.Pawn(Color.Black))   // g7
        .put(Position(6, 7), Piece.Pawn(Color.Black))   // h7
      val game  = Game(board, Color.White, GameStatus.Playing)
      val after = game.applyMove(Move(Position(6, 0), Position(7, 0))).get  // Ra7-Ra8#
      val ev    = GameEvent(1, ParsedMove("a7", "a8", None), game, after)
      ev.isCheck shouldBe true
    }

    "return false for a quiet move" in {
      val game  = Game.newGame
      val after = game.applyMove(Move(Position(1, 4), Position(3, 4))).get
      val ev    = GameEvent(1, ParsedMove("e2", "e4", None), game, after)
      ev.isCheck shouldBe false
    }
  }

  // ── GameStats ─────────────────────────────────────────────────────────────

  "GameStats.empty" should {

    "have all counters at zero" in {
      val s = GameStats.empty
      s.totalMoves   shouldBe 0
      s.captures     shouldBe 0
      s.checks       shouldBe 0
      s.invalidMoves shouldBe 0
      s.finalEval    shouldBe 0
      s.finalStatus  shouldBe GameStatus.Playing
    }
  }

  "GameStats.toString" should {

    "include all stat fields" in {
      val s   = GameStats(42, 5, 3, 1, 150, GameStatus.Playing)
      val str = s.toString
      str should include("42")
      str should include("5")
      str should include("3")
      str should include("1")
      str should include("+150")
      str should include("Playing")
    }

    "format a negative eval with a minus sign" in {
      GameStats(10, 0, 0, 0, -200, GameStatus.Playing).toString should include("-200")
    }

    "prefix positive eval with '+'" in {
      GameStats(10, 0, 0, 0, 75, GameStatus.Playing).toString should include("+75")
    }
  }
