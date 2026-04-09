package chess.util

trait Observable:
  private var observers: List[Observer] = List.empty

  def add(observer: Observer): Unit =
    observers = observer :: observers

  def remove(observer: Observer): Unit =
    observers = observers.filterNot(_ == observer)

  def notifyObservers(): Unit =
    observers.foreach(_.update())
