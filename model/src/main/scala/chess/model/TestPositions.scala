package chess.model

/** Classic chess test positions, selectable via GUI for easy testing. */
object TestPositions:

  case class TestPosition(name: String, fen: String, description: String)

  val positions: Vector[TestPosition] = Vector(

    TestPosition(
      "Startstellung",
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      "Standard-Anfangsstellung"
    ),

    TestPosition(
      "Scholar's Mate (1 Zug)",
      "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4",
      "Weiß setzt mit Qxf7# matt (Schäfermatt)"
    ),

    TestPosition(
      "Matt in 1 (Damenmatt)",
      "6k1/5ppp/8/8/8/8/1Q3PPP/6K1 w - - 0 1",
      "Weiß setzt mit Qb8# oder Qg7# matt"
    ),

    TestPosition(
      "Matt in 1 (Turmmatt)",
      "6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1",
      "Weiß setzt mit Ra8# matt"
    ),

    TestPosition(
      "Patt-Falle",
      "k7/8/1K6/8/8/8/8/1Q6 w - - 0 1",
      "Achtung: Qb8 und Qa2 setzen patt! Richtig: Qa1+ oder Qb7#"
    ),

    TestPosition(
      "En Passant Test",
      "rnbqkbnr/pppp1ppp/8/4pP2/8/8/PPPPP1PP/RNBQKBNR w KQkq e6 0 3",
      "Weiß kann en passant auf e6 schlagen (fxe6)"
    ),

    TestPosition(
      "Rochade Test",
      "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1",
      "Beide Seiten können kurz und lang rochieren"
    ),

    TestPosition(
      "Bauernumwandlung",
      "8/P5k1/8/8/8/8/6K1/8 w - - 0 1",
      "Weißer Bauer auf a7 kann umgewandelt werden (a8=Q)"
    ),

    TestPosition(
      "Lucena-Stellung (Turm)",
      "1K1k4/1P6/8/8/8/8/r7/5R2 w - - 0 1",
      "Klassisches Turmendspiel – Brückenbau-Technik"
    ),

    TestPosition(
      "Philidor-Stellung (Turm)",
      "8/8/8/4k3/4P3/4K3/8/r3R3 w - - 0 1",
      "Remis-Stellung im Turmendspiel"
    ),

    TestPosition(
      "Doppelschach",
      "r1bqk2r/pppp1Npp/2n2n2/2b1p3/2B1P3/8/PPPP1PPP/RNBQK2R b KQkq - 0 1",
      "Schwarz steht nach Sf7 im Doppelschach (Springer + Läufer)"
    ),

    TestPosition(
      "Ersticktes Matt",
      "r1b3kr/pppn1Npp/8/3pn1qQ/2B5/8/PPPP1PPP/RNB1K2R w KQ - 0 1",
      "Klassisches ersticktes Matt-Motiv mit Springer"
    ),

    TestPosition(
      "Unzureichendes Material",
      "8/8/4k3/8/8/4K3/8/8 w - - 0 1",
      "König gegen König – automatisches Remis"
    ),

    TestPosition(
      "K+L vs K (Remis)",
      "8/8/4k3/8/8/4K3/3B4/8 w - - 0 1",
      "König + Läufer gegen König – ungenügend zum Mattsetzen"
    ),

    TestPosition(
      "Komplexes Mittelspiel",
      "r1bq1rk1/pp2ppbp/2np1np1/8/3NP3/2N1BP2/PPPQ2PP/R3KB1R w KQ - 0 9",
      "Sizilianisch, Drachen-Variante – reiches taktisches Spiel"
    ),

    TestPosition(
      "FEN Eingabe",
      "",
      "Eigene FEN-Stellung eingeben"
    )
  )
