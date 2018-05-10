name := "RaftyMcRaftFace"

scalaVersion := "2.12.4"

scalacOptions += "-Ypartial-unification"

val circeDeps = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % "0.9.1")

val catsDep = "org.typelevel" %% "cats-core" % "1.0.1"

val fs2Deps = Seq(
  "co.fs2" %% "fs2-core",
  "co.fs2" %% "fs2-io"
).map(_ % "0.10.1")

val scalaTestDeps = Seq(
  "org.scalactic" %% "scalactic",
  "org.scalatest" %% "scalatest"
).map(_  % "3.0.5")

val diff = "ai.x" %% "diff" % "2.0"

val testDeps = (scalaTestDeps :+ diff).map(_ % "test")

libraryDependencies ++= (fs2Deps ++ circeDeps ++ testDeps :+ catsDep)