package chess.gatling.config

object GatlingConfig:
  val baseUrl = sys.env.getOrElse("BASE_URL", "http://localhost:3000")

  val smokeP95Ms   = 300
  val loadP95Ms    = 500
  val sfSmokeP95Ms = 2000
  val sfLoadP95Ms  = 5000

  val smokeVus     = 1
  val smokeDurSec  = 10
  val loadVus      = 10
  val loadDurSec   = 30
  val loadStartSec = 12
  val sfLoadVus    = 5

  val startingFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
