package chess.tournament.config

final case class TournamentConfig(
    serverUrl: String,
    botName: String,
    directorName: String,
    aiTimeLimitMs: Long,
    aiMaxDepth: Int,
)

object TournamentConfig:
  def fromEnv(): TournamentConfig =
    def env(key: String): Option[String] =
      Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty)
    def envInt(key: String, default: Int): Int =
      env(key).flatMap(_.toIntOption).getOrElse(default)
    def envLong(key: String, default: Long): Long =
      env(key).flatMap(_.toLongOption).getOrElse(default)

    TournamentConfig(
      serverUrl     = env("TOURNAMENT_SERVER_URL").getOrElse("https://tournament.maichess.berger-software.com"),
      botName       = env("TOURNAMENT_BOT_NAME").getOrElse("alu-chess-bot"),
      directorName  = env("TOURNAMENT_DIRECTOR_NAME").getOrElse("alu-chess-director"),
      aiTimeLimitMs = envLong("AI_TIME_LIMIT_MS", 2000L),
      aiMaxDepth    = envInt("AI_MAX_DEPTH", 4),
    )
