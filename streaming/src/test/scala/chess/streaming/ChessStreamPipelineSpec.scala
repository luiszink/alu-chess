package chess.streaming

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import chess.model.{Board, Color, Game, GameStatus, Move, Piece, Position}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*

class ChessStreamPipelineSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll:

  given system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "chess-stream-test")

  given ec: ExecutionContext = system.executionContext

  override def afterAll(): Unit =
    system.terminate()
    super.afterAll()

  private def run[A](f: => scala.concurrent.Future[A]): A =
    Await.result(f, 10.seconds)

  // ── parseFlow ─────────────────────────────────────────────────────────────

  "parseFlow" should {

    "parse a simple two-token move" in {
      val result = run(
        Source.single(ByteString("e2 e4\n"))
          .via(ChessStreamPipeline.parseFlow)
          .runWith(Sink.seq)
      )
      result shouldBe Vector(ParsedMove("e2", "e4", None))
    }

    "parse a move with promotion piece" in {
      val result = run(
        Source.single(ByteString("e7 e8 Q\n"))
          .via(ChessStreamPipeline.parseFlow)
          .runWith(Sink.seq)
      )
      result shouldBe Vector(ParsedMove("e7", "e8", Some('Q')))
    }

    "filter lines starting with '#'" in {
      val result = run(
        Source.single(ByteString("# Kommentar\ne2 e4\n"))
          .via(ChessStreamPipeline.parseFlow)
          .runWith(Sink.seq)
      )
      result shouldBe Vector(ParsedMove("e2", "e4", None))
    }

    "filter blank lines" in {
      val result = run(
        Source.single(ByteString("\n\ne2 e4\n\n"))
          .via(ChessStreamPipeline.parseFlow)
          .runWith(Sink.seq)
      )
      result shouldBe Vector(ParsedMove("e2", "e4", None))
    }

    "discard lines with wrong token count" in {
      val result = run(
        Source.single(ByteString("single\ne2 e4\n"))
          .via(ChessStreamPipeline.parseFlow)
          .runWith(Sink.seq)
      )
      result shouldBe Vector(ParsedMove("e2", "e4", None))
    }

    "parse multiple moves from a single ByteString chunk" in {
      val result = run(
        Source.single(ByteString("e2 e4\ne7 e5\nd2 d4\n"))
          .via(ChessStreamPipeline.parseFlow)
          .runWith(Sink.seq)
      )
      result shouldBe Vector(
        ParsedMove("e2", "e4", None),
        ParsedMove("e7", "e5", None),
        ParsedMove("d2", "d4", None),
      )
    }

    "handle multiple ByteString chunks (framing across chunks)" in {
      val result = run(
        Source(List(ByteString("e2 e"), ByteString("4\ne7 e5\n")))
          .via(ChessStreamPipeline.parseFlow)
          .runWith(Sink.seq)
      )
      result shouldBe Vector(
        ParsedMove("e2", "e4", None),
        ParsedMove("e7", "e5", None),
      )
    }
  }

  // ── gameProcessingFlow ────────────────────────────────────────────────────

  "gameProcessingFlow" should {

    "emit Right(GameEvent) for a valid opening move" in {
      val result = run(
        Source(List(ParsedMove("e2", "e4", None)))
          .via(ChessStreamPipeline.gameProcessingFlow(Game.newGame))
          .runWith(Sink.seq)
      )
      result should have size 1
      result.head shouldBe a[Right[?, ?]]
      result.head.toOption.get.moveNumber shouldBe 1
    }

    "emit Left(ParsedMove) for an illegal chess move" in {
      val result = run(
        Source(List(ParsedMove("e2", "e5", None)))  // pawn cannot jump 3 squares
          .via(ChessStreamPipeline.gameProcessingFlow(Game.newGame))
          .runWith(Sink.seq)
      )
      result should have size 1
      result.head shouldBe a[Left[?, ?]]
    }

    "emit Left for a move referencing an invalid board position" in {
      val result = run(
        Source(List(ParsedMove("z9", "a1", None)))
          .via(ChessStreamPipeline.gameProcessingFlow(Game.newGame))
          .runWith(Sink.seq)
      )
      result should have size 1
      result.head shouldBe a[Left[?, ?]]
    }

    "increment moveNumber correctly over a sequence of valid moves" in {
      val moves = List(
        ParsedMove("e2", "e4", None),
        ParsedMove("e7", "e5", None),
        ParsedMove("d2", "d4", None),
      )
      val result = run(
        Source(moves)
          .via(ChessStreamPipeline.gameProcessingFlow(Game.newGame))
          .runWith(Sink.seq)
      )
      result.collect { case Right(ev) => ev.moveNumber } shouldBe List(1, 2, 3)
    }

    "continue processing after an invalid move without aborting the stream" in {
      val moves = List(
        ParsedMove("e2", "e4", None),  // valid
        ParsedMove("z9", "a1", None),  // invalid position
        ParsedMove("e7", "e5", None),  // valid — stream must still reach this
      )
      val result = run(
        Source(moves)
          .via(ChessStreamPipeline.gameProcessingFlow(Game.newGame))
          .runWith(Sink.seq)
      )
      result should have size 3
      result(0) shouldBe a[Right[?, ?]]
      result(1) shouldBe a[Left[?, ?]]
      result(2) shouldBe a[Right[?, ?]]
    }

    "preserve the game state between moves (e4 e5 d4 sequence)" in {
      val moves = List(
        ParsedMove("e2", "e4", None),
        ParsedMove("e7", "e5", None),
        ParsedMove("d2", "d4", None),
      )
      val result = run(
        Source(moves)
          .via(ChessStreamPipeline.gameProcessingFlow(Game.newGame))
          .runWith(Sink.seq)
      )
      // all three must succeed — if state is lost, moves 2/3 would fail
      result.forall(_.isRight) shouldBe true
    }
  }

  // ── enrichFlow ────────────────────────────────────────────────────────────

  "enrichFlow" should {

    "produce evalScore=0 and moveNumber=0 for an invalid move" in {
      val result = run(
        Source(List(Left(ParsedMove("z9", "a1", None))))
          .via(ChessStreamPipeline.enrichFlow)
          .runWith(Sink.seq)
      )
      result should have size 1
      result.head.evalScore    shouldBe 0
      result.head.event.moveNumber shouldBe 0
    }

    "compute a finite eval score for a valid game event" in {
      val game  = Game.newGame
      val move  = Move(Position(1, 4), Position(3, 4))  // e2-e4
      val after = game.applyMove(move).get
      val ev    = GameEvent(1, ParsedMove("e2", "e4", None), game, after)

      val result = run(
        Source(List(Right(ev)))
          .via(ChessStreamPipeline.enrichFlow)
          .runWith(Sink.seq)
      )
      result should have size 1
      result.head.evalScore should be > -10000
      result.head.evalScore should be < 10000
    }

    "process mixed valid/invalid events in parallel (mapAsync)" in {
      val game  = Game.newGame
      val move  = Move(Position(1, 4), Position(3, 4))
      val after = game.applyMove(move).get
      val validEv   = Right(GameEvent(1, ParsedMove("e2", "e4", None), game, after))
      val invalidEv = Left(ParsedMove("z9", "a1", None))

      val result = run(
        Source(List(validEv, invalidEv, validEv))
          .via(ChessStreamPipeline.enrichFlow)
          .runWith(Sink.seq)
      )
      result should have size 3
      result(1).evalScore    shouldBe 0
      result(1).event.moveNumber shouldBe 0
    }
  }

  // ── aggregate ────────────────────────────────────────────────────────────

  "aggregate" should {

    "start from empty stats" in {
      val s = GameStats.empty
      s.totalMoves   shouldBe 0
      s.invalidMoves shouldBe 0
      s.captures     shouldBe 0
      s.checks       shouldBe 0
    }

    "count a valid quiet move" in {
      val game  = Game.newGame
      val after = game.applyMove(Move(Position(1, 4), Position(3, 4))).get
      val ev    = EnrichedEvent(GameEvent(1, ParsedMove("e2", "e4", None), game, after), 25)

      ChessStreamPipeline.aggregate(GameStats.empty, ev).totalMoves shouldBe 1
    }

    "count an invalid move (moveNumber == 0)" in {
      val ev = EnrichedEvent(GameEvent(0, ParsedMove("z9", "a1", None), Game.newGame, Game.newGame), 0)

      val stats = ChessStreamPipeline.aggregate(GameStats.empty, ev)
      stats.invalidMoves shouldBe 1
      stats.totalMoves   shouldBe 0
    }

    "count a capture" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(4, 0), Piece.Rook(Color.White))
        .put(Position(4, 7), Piece.Knight(Color.Black))  // h5 — will be captured
        .put(Position(7, 4), Piece.King(Color.Black))
      val game  = Game(board, Color.White, GameStatus.Playing)
      val after = game.applyMove(Move(Position(4, 0), Position(4, 7))).get
      val ev    = EnrichedEvent(GameEvent(1, ParsedMove("a5", "h5", None), game, after), 300)

      ChessStreamPipeline.aggregate(GameStats.empty, ev).captures shouldBe 1
    }

    "count a check" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(1, 0), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game  = Game(board, Color.White, GameStatus.Playing)
      val after = game.applyMove(Move(Position(1, 0), Position(7, 0))).get  // Ra2-Ra8+
      val ev    = EnrichedEvent(GameEvent(1, ParsedMove("a2", "a8", None), game, after), 500)

      ChessStreamPipeline.aggregate(GameStats.empty, ev).checks shouldBe 1
    }

    "accumulate stats across multiple events" in {
      val game1  = Game.newGame
      val after1 = game1.applyMove(Move(Position(1, 4), Position(3, 4))).get
      val ev1    = EnrichedEvent(GameEvent(1, ParsedMove("e2", "e4", None), game1, after1), 10)

      val after2 = after1.applyMove(Move(Position(6, 4), Position(4, 4))).get
      val ev2    = EnrichedEvent(GameEvent(2, ParsedMove("e7", "e5", None), after1, after2), -10)

      val invalid = EnrichedEvent(GameEvent(0, ParsedMove("z9", "a1", None), Game.newGame, Game.newGame), 0)

      val stats = List(ev1, ev2, invalid).foldLeft(GameStats.empty)(ChessStreamPipeline.aggregate)
      stats.totalMoves   shouldBe 2
      stats.invalidMoves shouldBe 1
    }

    "record the finalEval and finalStatus from the last event" in {
      val game  = Game.newGame
      val after = game.applyMove(Move(Position(1, 4), Position(3, 4))).get
      val ev    = EnrichedEvent(GameEvent(1, ParsedMove("e2", "e4", None), game, after), 42)

      val stats = ChessStreamPipeline.aggregate(GameStats.empty, ev)
      stats.finalEval   shouldBe 42
      stats.finalStatus shouldBe after.status
    }
  }
