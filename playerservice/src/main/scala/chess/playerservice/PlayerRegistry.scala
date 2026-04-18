package chess.playerservice

import cats.effect.*
import java.util.UUID

enum GameMode:
  case HumanVsHuman
  case HumanVsAI

enum SessionStatus:
  case Waiting
  case Active
  case Finished

case class Player(
  id: String,
  name: String,
  gameId: Option[String] = None,
  color: Option[String] = None,
)

case class GameSession(
  id: String,
  mode: GameMode,
  whitePlayerId: String,
  blackPlayerId: Option[String],
  status: SessionStatus,
)

private case class RegistryState(
  players:  Map[String, Player]      = Map.empty,
  sessions: Map[String, GameSession] = Map.empty,
)

class PlayerRegistry(ref: Ref[IO, RegistryState]):

  def registerPlayer(name: String): IO[Player] =
    val player = Player(UUID.randomUUID().toString, name)
    ref.update(s => s.copy(players = s.players + (player.id -> player))) >> IO.pure(player)

  def getPlayer(playerId: String): IO[Option[Player]] =
    ref.get.map(_.players.get(playerId))

  def getSession(gameId: String): IO[Option[GameSession]] =
    ref.get.map(_.sessions.get(gameId))

  def listWaitingSessions(): IO[List[GameSession]] =
    ref.get.map(_.sessions.values.filter(_.status == SessionStatus.Waiting).toList)

  def createHvAISession(playerId: String): IO[Either[String, GameSession]] =
    ref.modify { s =>
      s.players.get(playerId) match
        case None    => (s, Left(s"Player $playerId not found"))
        case Some(p) if p.gameId.isDefined => (s, Left("Player already in a game"))
        case Some(p) =>
          val gameId  = UUID.randomUUID().toString
          val session = GameSession(gameId, GameMode.HumanVsAI, playerId, None, SessionStatus.Active)
          val updated = p.copy(gameId = Some(gameId), color = Some("White"))
          val s2 = s.copy(
            players  = s.players  + (playerId -> updated),
            sessions = s.sessions + (gameId   -> session),
          )
          (s2, Right(session))
    }

  def createHvHSession(whitePlayerId: String): IO[Either[String, GameSession]] =
    ref.modify { s =>
      s.players.get(whitePlayerId) match
        case None    => (s, Left(s"Player $whitePlayerId not found"))
        case Some(p) if p.gameId.isDefined => (s, Left("Player already in a game"))
        case Some(p) =>
          val gameId  = UUID.randomUUID().toString
          val session = GameSession(gameId, GameMode.HumanVsHuman, whitePlayerId, None, SessionStatus.Waiting)
          val updated = p.copy(gameId = Some(gameId), color = Some("White"))
          val s2 = s.copy(
            players  = s.players  + (whitePlayerId -> updated),
            sessions = s.sessions + (gameId        -> session),
          )
          (s2, Right(session))
    }

  def joinHvHSession(gameId: String, blackPlayerId: String): IO[Either[String, GameSession]] =
    ref.modify { s =>
      (s.sessions.get(gameId), s.players.get(blackPlayerId)) match
        case (None, _)    => (s, Left(s"Session $gameId not found"))
        case (_, None)    => (s, Left(s"Player $blackPlayerId not found"))
        case (Some(sess), Some(player)) =>
          if sess.status != SessionStatus.Waiting then
            (s, Left("Session is not in Waiting state"))
          else if sess.whitePlayerId == blackPlayerId then
            (s, Left("Cannot join your own session"))
          else if player.gameId.isDefined then
            (s, Left("Player already in a game"))
          else
            val updSess   = sess.copy(blackPlayerId = Some(blackPlayerId), status = SessionStatus.Active)
            val updPlayer = player.copy(gameId = Some(gameId), color = Some("Black"))
            val s2 = s.copy(
              players  = s.players  + (blackPlayerId -> updPlayer),
              sessions = s.sessions + (gameId        -> updSess),
            )
            (s2, Right(updSess))
    }

  def markSessionFinished(gameId: String): IO[Unit] =
    ref.update { s =>
      s.sessions.get(gameId) match
        case None       => s
        case Some(sess) => s.copy(sessions = s.sessions + (gameId -> sess.copy(status = SessionStatus.Finished)))
    }

  def removePlayerFromSession(playerId: String): IO[Unit] =
    ref.update { s =>
      s.players.get(playerId) match
        case None    => s
        case Some(p) => s.copy(players = s.players + (playerId -> p.copy(gameId = None, color = None)))
    }

object PlayerRegistry:
  def make: IO[PlayerRegistry] =
    Ref.of[IO, RegistryState](RegistryState()).map(new PlayerRegistry(_))
