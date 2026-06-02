package chess.model.ai

import chess.model.Move

/** Fixed-size transposition table with "always replace" strategy. */
object TranspositionTable:

  enum Bound:
    case Exact, Lower, Upper

  final case class Entry(
    key:   Long,
    depth: Int,
    score: Int,
    bound: Bound,
    move:  Option[Move]
  )

  /** Default 16 MB table → 512k slots (≈ power of two for fast masking). */
  def apply(sizeMb: Int = 16): TranspositionTable =
    new TranspositionTable(sizeMb)

final class TranspositionTable private[ai] (sizeMb: Int):
  import TranspositionTable.*

  private val slots: Int =
    // Target ~32 B per entry. Round down to previous power of two.
    val target = (sizeMb.max(1).toLong * 1024L * 1024L / 32L).toInt.max(1024)
    Integer.highestOneBit(target)

  private val mask: Int = slots - 1
  private val table: Array[Entry | Null] = new Array[Entry | Null](slots)

  def probe(key: Long): Option[Entry] =
    val e = table((key & mask).toInt)
    if e != null && e.key == key then Some(e) else None

  def store(key: Long, depth: Int, score: Int, bound: Bound, move: Option[Move]): Unit =
    table((key & mask).toInt) = Entry(key, depth, score, bound, move)

  def clear(): Unit =
    var i = 0
    while i < slots do
      table(i) = null
      i += 1

  def size: Int = slots
