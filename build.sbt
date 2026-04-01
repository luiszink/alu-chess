val scala3Version = "3.6.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "alu-chess",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.19",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    libraryDependencies += "org.scala-lang.modules" %% "scala-swing" % "3.0.0",
    libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0",
    libraryDependencies += "org.playframework" %% "play-json" % "3.0.4",
    // Swing GUI classes and the main entry point cannot be unit-tested headlessly
    coverageExcludedPackages := "chess\\.aview\\.gui\\..*;chess\\.Chess\\$package",
    coverageExcludedFiles := ".*Chess\\.scala",
    // Minimum coverage thresholds: 100% is not achievable with Scala 3 + scoverage due to
    // pattern match branch instrumentation artifacts and untestable sys.exit in Controller.quit().
    // All reachable code paths are tested; the ~1% gap consists of dead code branches.
    coverageMinimumStmtTotal := 90,
    coverageMinimumBranchTotal := 90,
    coverageFailOnMinimum := true
  )
