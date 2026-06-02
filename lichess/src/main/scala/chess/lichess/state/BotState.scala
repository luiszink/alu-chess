package chess.lichess.state

import cats.effect.IO
import chess.lichess.state.LichessBotSession

/** Holds the current state of the bot connection so the HTTP layer can report
  * an accurate status instead of pretending the token is missing whenever
  * connection setup fails. */
enum BotState:
  case NotConfigured
  case Connecting
  case Failed(message: String)
  case Running(session: LichessBotSession)
