package chess.tournament.config

final case class TournamentConfig(
    port: Int,
    serverUrl: String,
    botName: String,
    aiTimeLimitMs: Long,
    aiMaxDepth: Int,
):
  def stagingUrl: String = "https://tournament.staging.maichess.berger-software.com"

object TournamentConfig:
  def fromEnv(): TournamentConfig =
    def env(key: String): Option[String] =
      Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty)
    def envInt(key: String, default: Int): Int =
      env(key).flatMap(_.toIntOption).getOrElse(default)
    def envLong(key: String, default: Long): Long =
      env(key).flatMap(_.toLongOption).getOrElse(default)

    TournamentConfig(
      port          = envInt("PORT", 8087),
      serverUrl     = env("TOURNAMENT_SERVER_URL").getOrElse("https://tournament.maichess.berger-software.com"),
      botName       = env("TOURNAMENT_BOT_NAME").getOrElse("alu-chess-bot"),
      aiTimeLimitMs = envLong("AI_TIME_LIMIT_MS", 2000L),
      aiMaxDepth    = envInt("AI_MAX_DEPTH", 4),
    )
