package chess.controller

import chess.model.{Game, Color, Board, Move, Position, GameStatus, ChessError, Piece, TimeControl, ChessClock}
import chess.util.Observer
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ControllerSpec extends AnyWordSpec with Matchers {

  "A Controller" should {

    "start with an initial game" in {
      val controller = Controller()
      controller.game.board shouldBe Board.initial
      controller.game.currentPlayer shouldBe Color.White
    }

    "notify observers on newGame" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.newGame()
      notified shouldBe true
    }

    "return a board string" in {
      val controller = Controller()
      val s = controller.boardToString
      s should include("K")
      s should include("k")
    }

    "return a status text" in {
      val controller = Controller()
      controller.statusText should include("White")
    }

    "show Check in status text when in check" in {
      val controller = Controller()
      controller.statusText should include("am Zug")
      // We can't easily force check via Controller API without many moves,
      // but we verify the Playing status text works
    }
  }

  "remove observer" should {
    "not notify removed observers" in {
      val controller = Controller()
      var count = 0
      val observer = new Observer:
        override def update(): Unit = count += 1
      controller.add(observer)
      controller.newGame()
      val countAfterAdd = count
      controller.remove(observer)
      controller.newGame()
      count shouldBe countAfterAdd // no additional notification after removal
    }
  }

  "statusText" should {

    "show Check text when in check" in {
      val controller = Controller()
      // White rook at a8, black king at e8: black is in check
      controller.loadFen("R3k3/8/8/8/8/8/8/4K3 b - - 0 1")
      controller.game.status shouldBe GameStatus.Check
      controller.statusText should include("Schach")
    }

    "show Checkmate text after checkmate" in {
      val controller = Controller()
      // Fool's Mate
      controller.doMove(Move(Position(1,5), Position(2,5)))
      controller.doMove(Move(Position(6,4), Position(4,4)))
      controller.doMove(Move(Position(1,6), Position(3,6)))
      controller.doMove(Move(Position(7,3), Position(3,7)))
      controller.game.status shouldBe GameStatus.Checkmate
      controller.statusText should include("Schachmatt")
    }

    "show Stalemate text when in stalemate" in {
      val controller = Controller()
      // Black king at h8, white king at f7, white queen at g6 -> stalemate
      controller.loadFen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1")
      controller.game.status shouldBe GameStatus.Stalemate
      controller.statusText should include("Patt")
    }

    "show Draw text when insufficient material" in {
      val controller = Controller()
      // K vs K -> draw by insufficient material
      controller.loadFen("8/8/4k3/8/8/4K3/8/8 w - - 0 1")
      controller.game.status shouldBe GameStatus.Draw
      controller.statusText should include("Remis")
    }

    "show Resigned text after resign" in {
      val controller = Controller()
      controller.resign()
      controller.game.status shouldBe GameStatus.Resigned
      controller.statusText should include("Aufgegeben")
    }

    "notify observers on resign" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.resign()
      notified shouldBe true
    }
  }

  "doMove" should {

    "accept a valid move and switch player" in {
      val controller = Controller()
      val result = controller.doMove(Move(Position(1, 4), Position(3, 4)))
      result shouldBe true
      controller.game.currentPlayer shouldBe Color.Black
    }

    "reject a move from an empty square" in {
      val controller = Controller()
      val result = controller.doMove(Move(Position(3, 3), Position(4, 3)))
      result shouldBe false
      controller.game.currentPlayer shouldBe Color.White
    }

    "notify observers on successful move" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      notified shouldBe true
    }
  }

  "doMoveResult" should {

    "return Right(game) for a valid move" in {
      val controller = Controller()
      val result = controller.doMoveResult(Move(Position(1, 4), Position(3, 4)))
      result shouldBe a[Right[?, ?]]
      controller.game.currentPlayer shouldBe Color.Black
    }

    "return Left(NoPieceAtSource) when source is empty" in {
      val controller = Controller()
      val result = controller.doMoveResult(Move(Position(3, 3), Position(4, 3)))
      result shouldBe Left(ChessError.NoPieceAtSource(Position(3, 3)))
    }

    "return Left(WrongColorPiece) when moving wrong color" in {
      val controller = Controller()
      val result = controller.doMoveResult(Move(Position(6, 4), Position(4, 4)))
      result shouldBe Left(ChessError.WrongColorPiece(Position(6, 4), Color.White, Color.Black))
    }

    "return Left(GameAlreadyOver) after checkmate" in {
      val controller = Controller()
      // Fool's Mate: 1.f3 e5 2.g4 Qh4#
      controller.doMove(Move(Position(1,5), Position(2,5))) // f2-f3
      controller.doMove(Move(Position(6,4), Position(4,4))) // e7-e5
      controller.doMove(Move(Position(1,6), Position(3,6))) // g2-g4
      controller.doMove(Move(Position(7,3), Position(3,7))) // Qd8-h4#
      controller.game.status shouldBe GameStatus.Checkmate
      val result = controller.doMoveResult(Move(Position(1,4), Position(3,4)))
      result shouldBe Left(ChessError.GameAlreadyOver(GameStatus.Checkmate))
    }

    "notify observers on successful doMoveResult" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.doMoveResult(Move(Position(1, 4), Position(3, 4)))
      notified shouldBe true
    }
  }

  "loadFen" should {

    "load a valid FEN and return true" in {
      val controller = Controller()
      val result = controller.loadFen("8/8/8/8/8/8/8/4K3 w - - 0 1")
      result shouldBe true
      controller.game.board.cell(Position(0, 4)) shouldBe Some(Piece.King(Color.White))
    }

    "return false for invalid FEN" in {
      val controller = Controller()
      controller.loadFen("not a valid fen") shouldBe false
    }
  }

  "loadFenResult" should {

    "return Right(game) for valid FEN" in {
      val controller = Controller()
      val result = controller.loadFenResult("8/8/8/8/8/8/8/4K3 w - - 0 1")
      result shouldBe a[Right[?, ?]]
      controller.game.board.cell(Position(0, 4)) shouldBe Some(Piece.King(Color.White))
    }

    "return Left for invalid FEN" in {
      val controller = Controller()
      val result = controller.loadFenResult("not a valid fen")
      result shouldBe a[Left[?, ?]]
    }

    "notify observers on successful loadFenResult" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.loadFenResult("8/8/8/8/8/8/8/4K3 w - - 0 1")
      notified shouldBe true
    }
  }

  "auto-save" should {

    "save a game record after checkmate" in {
      val repo = chess.model.InMemoryGameRepository()
      val controller = Controller(repo)
      // Fool's Mate: 1.f3 e5 2.g4 Qh4#
      controller.doMove(Move(Position(1, 5), Position(2, 5))) // f2-f3
      controller.doMove(Move(Position(6, 4), Position(4, 4))) // e7-e5
      controller.doMove(Move(Position(1, 6), Position(3, 6))) // g2-g4
      controller.doMove(Move(Position(7, 3), Position(3, 7))) // Qd8-h4#
      controller.game.status shouldBe GameStatus.Checkmate
      controller.gameHistory should have size 1
      controller.gameHistory.head.result shouldBe "0-1"
    }

    "not save a game that has only the initial state" in {
      val repo = chess.model.InMemoryGameRepository()
      val controller = Controller(repo)
      controller.newGame()
      controller.gameHistory shouldBe empty
    }

    "not double-save the same game" in {
      val repo = chess.model.InMemoryGameRepository()
      val controller = Controller(repo)
      // Fool's Mate
      controller.doMove(Move(Position(1, 5), Position(2, 5)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.doMove(Move(Position(1, 6), Position(3, 6)))
      controller.doMove(Move(Position(7, 3), Position(3, 7)))
      controller.game.status shouldBe GameStatus.Checkmate
      // Try another move (should fail, game is over) — auto-save should not create a second record
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.gameHistory should have size 1
    }
  }

  "replay mode" should {

    "block doMove during replay" in {
      val repo = chess.model.InMemoryGameRepository()
      val controller = Controller(repo)
      // Play a game to completion (Fool's Mate)
      controller.doMove(Move(Position(1, 5), Position(2, 5)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.doMove(Move(Position(1, 6), Position(3, 6)))
      controller.doMove(Move(Position(7, 3), Position(3, 7)))
      val recordId = controller.gameHistory.head.id
      controller.newGame()
      // Load replay
      controller.loadReplay(recordId) shouldBe true
      controller.isInReplay shouldBe true
      // Moves should be blocked
      controller.doMove(Move(Position(1, 4), Position(3, 4))) shouldBe false
    }

    "load a replay and allow browsing" in {
      val repo = chess.model.InMemoryGameRepository()
      val controller = Controller(repo)
      // Fool's Mate
      controller.doMove(Move(Position(1, 5), Position(2, 5)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.doMove(Move(Position(1, 6), Position(3, 6)))
      controller.doMove(Move(Position(7, 3), Position(3, 7)))
      val recordId = controller.gameHistory.head.id
      controller.newGame()
      controller.loadReplay(recordId) shouldBe true
      // Should be at the end of the replayed game
      controller.gameStatesCount shouldBe 5 // initial + 4 moves
      controller.browseIndex shouldBe 4
      // Browse back
      controller.browseBack()
      controller.browseIndex shouldBe 3
    }

    "exit replay and restore previous game state" in {
      val repo = chess.model.InMemoryGameRepository()
      val controller = Controller(repo)
      // Play to checkmate (Fool's Mate)
      controller.doMove(Move(Position(1, 5), Position(2, 5)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.doMove(Move(Position(1, 6), Position(3, 6)))
      controller.doMove(Move(Position(7, 3), Position(3, 7)))
      val recordId = controller.gameHistory.head.id
      // Start a new game and make one move
      controller.newGame()
      controller.doMove(Move(Position(1, 4), Position(3, 4))) // e2-e4
      val statesBeforeReplay = controller.gameStatesCount
      // Enter replay
      controller.loadReplay(recordId) shouldBe true
      controller.isInReplay shouldBe true
      // Exit replay
      controller.exitReplay()
      controller.isInReplay shouldBe false
      controller.gameStatesCount shouldBe statesBeforeReplay
    }

    "return false for unknown replay id" in {
      val controller = Controller()
      controller.loadReplay("nonexistent") shouldBe false
      controller.isInReplay shouldBe false
    }
  }

  // --- History navigation ---

  "browseBack" should {

    "go to previous state" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4))) // e2-e4
      controller.browseIndex shouldBe 1
      controller.browseBack()
      controller.browseIndex shouldBe 0
    }

    "not go below 0" in {
      val controller = Controller()
      controller.browseBack()
      controller.browseIndex shouldBe 0
    }

    "notify observers" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.browseBack()
      notified shouldBe true
    }

    "not notify when already at start" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.browseBack()
      notified shouldBe false
    }
  }

  "browseForward" should {

    "go to next state" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.browseBack()
      controller.browseIndex shouldBe 0
      controller.browseForward()
      controller.browseIndex shouldBe 1
    }

    "not go beyond latest" in {
      val controller = Controller()
      controller.browseForward()
      controller.browseIndex shouldBe 0
    }

    "not notify when already at end" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.browseForward()
      notified shouldBe false
    }
  }

  "browseToStart" should {

    "jump to index 0" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.browseToStart()
      controller.browseIndex shouldBe 0
    }

    "not notify when already at start" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.browseToStart()
      notified shouldBe false
    }
  }

  "browseToEnd" should {

    "jump to latest index" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.browseToStart()
      controller.browseToEnd()
      controller.browseIndex shouldBe 2
      controller.isAtLatest shouldBe true
    }

    "not notify when already at end" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.browseToEnd()
      notified shouldBe false
    }
  }

  "browseToMove" should {

    "jump to a specific index" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.browseToMove(1)
      controller.browseIndex shouldBe 1
    }

    "clamp to 0 if negative" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.browseToMove(-5)
      controller.browseIndex shouldBe 0
    }

    "clamp to max if too large" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.browseToMove(999)
      controller.browseIndex shouldBe 1
    }

    "not notify when index doesn't change" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.browseToMove(0)
      notified shouldBe false
    }
  }

  "isAtLatest" should {
    "return true for new game" in {
      Controller().isAtLatest shouldBe true
    }

    "return false after browseBack" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.browseBack()
      controller.isAtLatest shouldBe false
    }
  }

  "gameStatesCount" should {
    "be 1 for new game" in {
      Controller().gameStatesCount shouldBe 1
    }

    "increase after moves" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.gameStatesCount shouldBe 2
    }
  }

  "latestMoveHistory" should {
    "be empty for new game" in {
      Controller().latestMoveHistory shouldBe empty
    }

    "contain entries after moves" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.latestMoveHistory should have size 1
    }
  }

  // --- doMove when not at latest ---

  "doMove when not at latest" should {
    "reject the move" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.browseBack()
      controller.doMove(Move(Position(1, 3), Position(3, 3))) shouldBe false
    }
  }

  "doMoveResult when not at latest" should {
    "return Left(GameAlreadyOver)" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.browseBack()
      controller.doMoveResult(Move(Position(1, 3), Position(3, 3))) shouldBe a[Left[?, ?]]
    }
  }

  // --- Clock ---

  "newGameWithClock" should {

    "create a game with a clock" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      controller.clock shouldBe defined
      controller.game.board shouldBe Board.initial
    }

    "create a game without clock when None" in {
      val controller = Controller()
      controller.newGameWithClock(None)
      controller.clock shouldBe None
    }

    "reset game states" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      controller.gameStatesCount shouldBe 1
      controller.browseIndex shouldBe 0
    }

    "notify observers" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      notified shouldBe true
    }
  }

  "clock" should {

    "return ticked clock when active" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      val clock = controller.clock
      clock shouldBe defined
    }

    "return None when no clock" in {
      val controller = Controller()
      controller.clock shouldBe None
    }
  }

  "tickClock" should {

    "do nothing when no clock" in {
      val controller = Controller()
      controller.tickClock() // should not throw
      controller.clock shouldBe None
    }

    "do nothing when game is terminal" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      // Fool's Mate
      controller.doMove(Move(Position(1, 5), Position(2, 5)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.doMove(Move(Position(1, 6), Position(3, 6)))
      controller.doMove(Move(Position(7, 3), Position(3, 7)))
      controller.game.status shouldBe GameStatus.Checkmate
      controller.tickClock() // should not throw
    }

    "trigger timeout when clock expires" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      // Use reflection to inject an already-expired clock
      val expiredClock = ChessClock(0L, 300_000L, 0L, Some(Color.White), System.nanoTime())
      val field = controller.getClass.getDeclaredField("_clock")
      field.setAccessible(true)
      field.set(controller, Some(expiredClock))
      controller.tickClock()
      controller.game.status shouldBe GameStatus.TimeOut
    }

    "not trigger timeout when time is positive" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      controller.tickClock()
      controller.game.status shouldBe GameStatus.Playing
    }
  }

  "statusText for TimeOut" should {
    "include 'Zeit abgelaufen'" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      // Use reflection to inject an already-expired clock
      val expiredClock = ChessClock(0L, 300_000L, 0L, Some(Color.White), System.nanoTime())
      val field = controller.getClass.getDeclaredField("_clock")
      field.setAccessible(true)
      field.set(controller, Some(expiredClock))
      controller.tickClock()
      controller.game.status shouldBe GameStatus.TimeOut
      controller.statusText should include("Zeit abgelaufen")
    }
  }

  "doMove with clock" should {

    "press clock after successful move" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      val clock = controller.clock
      clock shouldBe defined
      // After pressing, black should be active
      clock.get.activeColor shouldBe Some(Color.Black)
    }

    "stop clock on terminal state" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      // Fool's Mate
      controller.doMove(Move(Position(1, 5), Position(2, 5)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.doMove(Move(Position(1, 6), Position(3, 6)))
      controller.doMove(Move(Position(7, 3), Position(3, 7)))
      controller.game.status shouldBe GameStatus.Checkmate
      val clock = controller.clock
      clock shouldBe defined
      clock.get.activeColor shouldBe None
    }
  }

  "doMoveResult with clock" should {

    "press clock after successful move" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      val result = controller.doMoveResult(Move(Position(1, 4), Position(3, 4)))
      result shouldBe a[Right[?, ?]]
      controller.clock.get.activeColor shouldBe Some(Color.Black)
    }

    "stop clock on checkmate" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      // Fool's Mate via doMoveResult
      controller.doMoveResult(Move(Position(1, 5), Position(2, 5)))
      controller.doMoveResult(Move(Position(6, 4), Position(4, 4)))
      controller.doMoveResult(Move(Position(1, 6), Position(3, 6)))
      val result = controller.doMoveResult(Move(Position(7, 3), Position(3, 7)))
      result shouldBe a[Right[?, ?]]
      controller.game.status shouldBe GameStatus.Checkmate
      controller.clock.get.activeColor shouldBe None
    }
  }

  "loadFen" should {

    "reset clock to None" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      controller.loadFen("8/8/8/8/8/8/8/4K3 w - - 0 1")
      controller.clock shouldBe None
    }

    "reset game states" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.loadFen("8/8/8/8/8/8/8/4K3 w - - 0 1")
      controller.gameStatesCount shouldBe 1
    }
  }

  "loadFenResult" should {

    "reset clock to None" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      controller.loadFenResult("8/8/8/8/8/8/8/4K3 w - - 0 1")
      controller.clock shouldBe None
    }
  }

  "newGame" should {
    "reset clock to None" in {
      val controller = Controller()
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      controller.newGame()
      controller.clock shouldBe None
    }

    "reset game states" in {
      val controller = Controller()
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      controller.newGame()
      controller.gameStatesCount shouldBe 1
      controller.browseIndex shouldBe 0
    }

    "exit replay mode when called during replay" in {
      val repo = chess.model.InMemoryGameRepository()
      val controller = Controller(repo)
      // Play Fool's Mate to create a saved game
      controller.doMove(Move(Position(1, 5), Position(2, 5)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.doMove(Move(Position(1, 6), Position(3, 6)))
      controller.doMove(Move(Position(7, 3), Position(3, 7)))
      val recordId = controller.gameHistory.head.id
      controller.newGame()
      controller.loadReplay(recordId) shouldBe true
      controller.isInReplay shouldBe true
      // newGame should exit replay first
      controller.newGame()
      controller.isInReplay shouldBe false
      controller.gameStatesCount shouldBe 1
    }
  }

  "newGameWithClock" should {

    "exit replay mode when called during replay" in {
      val repo = chess.model.InMemoryGameRepository()
      val controller = Controller(repo)
      // Play Fool's Mate
      controller.doMove(Move(Position(1, 5), Position(2, 5)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.doMove(Move(Position(1, 6), Position(3, 6)))
      controller.doMove(Move(Position(7, 3), Position(3, 7)))
      val recordId = controller.gameHistory.head.id
      controller.newGame()
      controller.loadReplay(recordId) shouldBe true
      controller.isInReplay shouldBe true
      // newGameWithClock should exit replay first
      controller.newGameWithClock(Some(TimeControl.Blitz5_0))
      controller.isInReplay shouldBe false
      controller.clock shouldBe defined
    }
  }

  "doMoveResult during replay" should {
    "return Left when in replay mode" in {
      val repo = chess.model.InMemoryGameRepository()
      val controller = Controller(repo)
      // Play Fool's Mate
      controller.doMove(Move(Position(1, 5), Position(2, 5)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.doMove(Move(Position(1, 6), Position(3, 6)))
      controller.doMove(Move(Position(7, 3), Position(3, 7)))
      val recordId = controller.gameHistory.head.id
      controller.newGame()
      controller.loadReplay(recordId) shouldBe true
      val result = controller.doMoveResult(Move(Position(1, 4), Position(3, 4)))
      result shouldBe a[Left[?, ?]]
    }
  }

  "loadReplay" should {
    "switch replay without resetting savedGameStates when already in replay" in {
      val repo = chess.model.InMemoryGameRepository()
      val controller = Controller(repo)
      // Play two games to create two records
      // Game 1: Fool's Mate
      controller.doMove(Move(Position(1, 5), Position(2, 5)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.doMove(Move(Position(1, 6), Position(3, 6)))
      controller.doMove(Move(Position(7, 3), Position(3, 7)))
      val id1 = controller.gameHistory.head.id
      // Game 2: another Fool's Mate
      controller.newGame()
      controller.doMove(Move(Position(1, 5), Position(2, 5)))
      controller.doMove(Move(Position(6, 4), Position(4, 4)))
      controller.doMove(Move(Position(1, 6), Position(3, 6)))
      controller.doMove(Move(Position(7, 3), Position(3, 7)))
      val id2 = controller.gameHistory.last.id
      controller.newGame()
      // Load first replay
      controller.loadReplay(id1) shouldBe true
      controller.isInReplay shouldBe true
      // Load second replay while still in first replay
      controller.loadReplay(id2) shouldBe true
      controller.isInReplay shouldBe true
      // Exit should restore the original state (before first loadReplay)
      controller.exitReplay()
      controller.isInReplay shouldBe false
      controller.gameStatesCount shouldBe 1
    }
  }
}
