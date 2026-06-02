package chess.model

/** Timeline state for script-style game navigation with undo/redo semantics. */
case class GameTimeline private (states: Vector[Game], index: Int):
  require(states.nonEmpty, "states must not be empty")
  require(index >= 0 && index < states.size, "index must point to an existing state")

  def current: Game = states(index)

  def isAtLatest: Boolean = index == states.size - 1

  def canUndo: Boolean = index > 0

  def canRedo: Boolean = index < states.size - 1

  def undo: GameTimeline =
    if canUndo then copy(index = index - 1) else this

  def redo: GameTimeline =
    if canRedo then copy(index = index + 1) else this

  /** Appends a new game state and drops future states when currently browsing history. */
  def append(next: Game): GameTimeline =
    val kept = states.take(index + 1)
    GameTimeline(kept :+ next, kept.size)

object GameTimeline:
  def fromGame(game: Game): GameTimeline = GameTimeline(Vector(game), 0)

/** A small state+error monad to script game transitions declaratively. */
final case class GameScript[+A](run: GameTimeline => Either[ChessError, (GameTimeline, A)]):
  def map[B](f: A => B): GameScript[B] =
    GameScript(timeline => run(timeline).map((next, a) => (next, f(a))))

  def flatMap[B](f: A => GameScript[B]): GameScript[B] =
    GameScript(timeline => run(timeline).flatMap((next, a) => f(a).run(next)))

  def eval(initial: GameTimeline): Either[ChessError, A] =
    run(initial).map(_._2)

  def exec(initial: GameTimeline): Either[ChessError, GameTimeline] =
    run(initial).map(_._1)

object GameScript:
  def pure[A](value: A): GameScript[A] =
    GameScript(timeline => Right((timeline, value)))

  def fail[A](error: ChessError): GameScript[A] =
    GameScript(_ => Left(error))

  def fromEither[A](result: Either[ChessError, A]): GameScript[A] =
    result match
      case Right(value) => pure(value)
      case Left(error)  => fail(error)

  val timeline: GameScript[GameTimeline] =
    GameScript(current => Right((current, current)))

  def modify(f: GameTimeline => GameTimeline): GameScript[Unit] =
    GameScript(current =>
      val updated = f(current)
      Right((updated, ()))
    )

  val currentGame: GameScript[Game] =
    timeline.map(_.current)

  val undo: GameScript[Game] =
    GameScript(current =>
      val updated = current.undo
      Right((updated, updated.current))
    )

  val redo: GameScript[Game] =
    GameScript(current =>
      val updated = current.redo
      Right((updated, updated.current))
    )

  def applyMove(move: Move): GameScript[Game] =
    for
      current <- currentGame
      next    <- fromEither(current.applyMoveE(move))
      _       <- modify(_.append(next))
    yield next

  def applyMoveString(input: String): GameScript[Game] =
    for
      move <- fromEither(Move.fromStringE(input))
      next <- applyMove(move)
    yield next

  def applyMoves(moves: Iterable[Move]): GameScript[Game] =
    moves.foldLeft(currentGame)((script, move) => script.flatMap(_ => applyMove(move)))

  def runOn[A](initialGame: Game)(script: GameScript[A]): Either[ChessError, (GameTimeline, A)] =
    script.run(GameTimeline.fromGame(initialGame))