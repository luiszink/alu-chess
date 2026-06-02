package chess.model.ai

import chess.model.Color

/** Describes whether and how the AI is participating in the current game. */
enum AIMode:
  /** Human vs. Human — AI is disabled. */
  case Disabled
  /** The AI plays as the given color; the other color is controlled by the human. */
  case PlayingAs(color: Color)
  /** Both sides are controlled by the AI. */
  case PlayingBoth
