val scala3Version = "3.6.4"

val http4sVersion = "0.23.30"
val circeVersion  = "0.14.10"

// Shared settings for all subprojects
lazy val commonSettings = Seq(
  version      := "0.1.0-SNAPSHOT",
  scalaVersion := scala3Version,
  libraryDependencies ++= Seq(
    "org.scalactic" %% "scalactic" % "3.2.19",
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  ),
)

// ── Model module ──────────────────────────────────────────────
// Pure domain logic (Board, Game, MoveValidator, FEN/PGN, …).
// Stateless – no dependency on Controller or View.
lazy val model = project
  .in(file("model"))
  .settings(
    commonSettings,
    name := "alu-chess-model",
    libraryDependencies ++= Seq(
      // Parser combinators for FEN/PGN
      "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0",
      // JSON (Circe)
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,
      // Http4s (REST API for Model-Service)
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-circe"        % http4sVersion,
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
    ),
    coverageMinimumStmtTotal   := 90,
    coverageMinimumBranchTotal := 90,
    coverageFailOnMinimum      := true,
  )

// ── Controller module ─────────────────────────────────────────
// Game state management, orchestration, Clock, REST API for Web UI.
// Depends on Model for domain types.
lazy val controller = project
  .in(file("controller"))
  .dependsOn(model)
  .settings(
    commonSettings,
    name := "alu-chess-controller",
    libraryDependencies ++= Seq(
      // Swing GUI
      "org.scala-lang.modules" %% "scala-swing" % "3.0.0",
      // Http4s (REST API for Controller-Service + HTTP client to call Model-Service)
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-circe"        % http4sVersion,
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
    ),
    coverageExcludedPackages := "chess\\.aview\\.gui\\..*;chess\\.Chess\\$package",
    coverageExcludedFiles    := ".*Chess\\.scala",
    coverageMinimumStmtTotal   := 90,
    coverageMinimumBranchTotal := 90,
    coverageFailOnMinimum      := true,
  )

// ── Root aggregate ────────────────────────────────────────────
lazy val root = project
  .in(file("."))
  .aggregate(model, controller)
  .settings(
    name := "alu-chess",
    publish / skip := true,
    Compile / unmanagedSourceDirectories := Nil,
    Test / unmanagedSourceDirectories    := Nil,
  )
