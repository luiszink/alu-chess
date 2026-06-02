# alu-chess Move DSL
# Format: <from> <to> [promotion]
# Kommentare mit # werden ignoriert, Leerzeilen ebenfalls.
#
# Partie: Ruy Lopez – Morphy Verteidigung
# Dieses Format ist Kafka-ready: jede Zeile entspricht einer Kafka-Message.
# Nächste Woche wird FileIO durch einen Kafka-Consumer ersetzt;
# die Flows (parseFlow, gameProcessingFlow, enrichFlow) bleiben unverändert.

# Eröffnung: Ruy Lopez
e2 e4
e7 e5
g1 f3
b8 c6
f1 b5
a7 a6
b5 a4
g8 f6
e1 g1
f8 e7
f1 e1
b7 b5
a4 b3
d7 d6
c2 c3
e8 g8

# Absichtlich ungültiger Zug – demonstriert Fehlerbehandlung ohne Stream-Abbruch
z9 z9

# Partie geht weiter
h2 h3
c6 a5
b3 c2
c7 c5
d2 d4
d8 c7
b1 d2
c8 d7
d2 f1
a5 c6
f1 e3
c6 a5
d1 e2
c5 d4
c3 d4
e5 d4
e3 d5
f6 d5
e4 d5
a5 b3
a1 b1
b3 d2
e1 d1
d2 f3
g2 f3
d7 h3
d1 d3
h3 g4
e2 e4
c7 c5
c1 e3
f8 d8
b2 b4
