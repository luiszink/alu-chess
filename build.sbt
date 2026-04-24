val scala3Version = "3.6.4"

val http4sVersion = "0.23.30"
val circeVersion  = "0.14.10"
val slickVersion      = "3.5.2"
val mongo4catsVersion = "0.7.11"

val assemblySettings = Seq(
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", "services", _*) => MergeStrategy.concat
    case PathList("META-INF", _*)             => MergeStrategy.discard
    case "reference.conf"                     => MergeStrategy.concat
    case _                                    => MergeStrategy.first
  }
)

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
    assemblySettings,
    name := "alu-chess-model",
    assembly / mainClass := Some("chess.model.api.ModelServer"),
    libraryDependencies ++= Seq(
      // Parser combinators for FEN/PGN
      "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0",
      // JSON (Circe)
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,
      // Http4s (REST API for Model-Service)
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-circe"        % http4sVersion,
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
      // Slick + PostgreSQL (Persistence)
      "com.typesafe.slick" %% "slick"          % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
      "org.postgresql"      % "postgresql"      % "42.7.4",
      // MongoDB via mongo4cats (Cats Effect wrapper)
      "io.github.kirill5k" %% "mongo4cats-core"  % mongo4catsVersion,
      "io.github.kirill5k" %% "mongo4cats-circe" % mongo4catsVersion,
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
    assemblySettings,
    name := "alu-chess-controller",
    assembly / mainClass := Some("chess.controller.api.ControllerServer"),
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

// ── PlayerService module ──────────────────────────────────────
// Player registration and matchmaking. No dependency on model or controller.
lazy val playerservice = project
  .in(file("playerservice"))
  .settings(
    commonSettings,
    assemblySettings,
    name := "alu-chess-playerservice",
    assembly / mainClass := Some("chess.playerservice.api.PlayerServer"),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-circe"        % http4sVersion,
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
    ),
  )

// ── Root aggregate ────────────────────────────────────────────
lazy val root = project
  .in(file("."))
  .aggregate(model, controller, playerservice)
  .settings(
    name := "alu-chess",
    publish / skip := true,
    Compile / unmanagedSourceDirectories := Nil,
    Test / unmanagedSourceDirectories    := Nil,
  )

addCommandAlias(
  "runAll",
  ";model/bgRunMain chess.model.api.ModelServer" +
  " ;playerservice/bgRunMain chess.playerservice.api.PlayerServer" +
  " ;controller/runMain chess.controller.api.ControllerServer"
)
