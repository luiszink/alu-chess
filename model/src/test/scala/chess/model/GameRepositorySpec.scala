package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import scala.compiletime.uninitialized

class GameRepositorySpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  var repo: InMemoryGameRepository = uninitialized

  override def beforeEach(): Unit =
    repo = InMemoryGameRepository()

  private def makeRecord(id: String = "test-id"): GameRecord =
    val states = Vector(Game.newGame)
    GameRecord(
      id = id,
      datePlayed = java.time.LocalDateTime.now(),
      result = "*",
      timeControl = None,
      moveCount = 0,
      pgn = "",
      gameStates = states
    )

  "InMemoryGameRepository" should {

    "start empty" in {
      repo.findAll() shouldBe empty
    }

    "save and retrieve a record" in {
      val record = makeRecord("r1")
      repo.save(record)
      repo.findAll() should have size 1
      repo.findAll().head.id shouldBe "r1"
    }

    "prepend new records (newest first)" in {
      repo.save(makeRecord("r1"))
      repo.save(makeRecord("r2"))
      val all = repo.findAll()
      all.map(_.id) shouldBe Vector("r2", "r1")
    }

    "find a record by id" in {
      val record = makeRecord("r1")
      repo.save(record)
      repo.findById("r1") shouldBe Some(record)
    }

    "return None for unknown id" in {
      repo.findById("unknown") shouldBe None
    }

    "delete a record by id" in {
      repo.save(makeRecord("r1"))
      repo.save(makeRecord("r2"))
      repo.delete("r1")
      repo.findAll().map(_.id) shouldBe Vector("r2")
      repo.findById("r1") shouldBe None
    }

    "clear all records" in {
      repo.save(makeRecord("r1"))
      repo.save(makeRecord("r2"))
      repo.clear()
      repo.findAll() shouldBe empty
    }

    "export a record as JSON by id" in {
      val record = makeRecord("r-json")
      repo.save(record)

      val json = repo.exportRecordAsJson("r-json")

      json shouldBe a[Right[?, ?]]
      json.toOption.get should include("\"id\":\"r-json\"")
      json.toOption.get should include("\"moves\"")
    }

    "import a record from JSON" in {
      val original = makeRecord("r-import")
      val json = GameJson.toRecordJsonString(original)

      val imported = repo.importRecordFromJson(json)

      imported shouldBe a[Right[?, ?]]
      repo.findById("r-import") shouldBe defined
      repo.findById("r-import").get.gameStates.map(Fen.toFen) shouldBe original.gameStates.map(Fen.toFen)
    }

    "return Left when exporting an unknown id as JSON" in {
      val missing = repo.exportRecordAsJson("missing-id")
      missing shouldBe a[Left[?, ?]]
    }
  }
}
