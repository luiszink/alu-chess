package chess.lichess.config

/** Static configuration loaded from environment variables.
  *
  * Backed by `LichessConfig.fromEnv`, all values fall back to safe MVP defaults
  * so the service can boot and report `not configured` rather than crash. */
final case class LichessConfig(
    port: Int,
    baseUrl: String,
    botToken: Option[String],
    autoAccept: Boolean,
    acceptRated: Boolean,
    acceptVariants: Set[String],
    minInitialSeconds: Int,
    maxInitialSeconds: Int,
    maxGamesConcurrent: Int,
    aiTimeLimitMs: Long,
    aiMaxDepth: Int,
):
  def hasToken: Boolean = botToken.exists(_.nonEmpty)

object LichessConfig:

  def fromEnv(): LichessConfig =
    def env(key: String): Option[String] =
      Option(System.getenv(key)).map(_.trim).filter(_.nonEmpty)
    def envInt(key: String, default: Int): Int  = env(key).flatMap(_.toIntOption).getOrElse(default)
    def envLong(key: String, default: Long): Long = env(key).flatMap(_.toLongOption).getOrElse(default)
    def envBool(key: String, default: Boolean): Boolean =
      env(key).map(_.equalsIgnoreCase("true")).getOrElse(default)

    LichessConfig(
      port               = envInt("PORT", 8085),
      baseUrl            = env("LICHESS_BASE_URL").getOrElse("https://lichess.org"),
      botToken           = env("LICHESS_BOT_TOKEN"),
      autoAccept         = envBool("LICHESS_AUTO_ACCEPT", true),
      acceptRated        = envBool("LICHESS_ACCEPT_RATED", false),
      acceptVariants     = env("LICHESS_ACCEPT_VARIANTS")
        .getOrElse("standard")
        .split(",")
        .map(_.trim.toLowerCase)
        .filter(_.nonEmpty)
        .toSet,
      minInitialSeconds  = envInt("LICHESS_MIN_INITIAL_SECONDS", 180),
      maxInitialSeconds  = envInt("LICHESS_MAX_INITIAL_SECONDS", 900),
      maxGamesConcurrent = envInt("LICHESS_MAX_GAMES_CONCURRENT", 2),
      aiTimeLimitMs      = envLong("AI_TIME_LIMIT_MS", 2000L),
      aiMaxDepth         = envInt("AI_MAX_DEPTH", 4),
    )
