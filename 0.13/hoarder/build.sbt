name := "hoarder"

version := "1.0.1-SNAPSHOT"

scalaVersion := "2.10.6"

sbtPlugin := true

organization := "com.github.romanowski"

(unmanagedSourceDirectories in Compile) += baseDirectory.value / "src" / "main" / "sbt_0.13"
(unmanagedSourceDirectories in Test) += baseDirectory.value / "src" / "test" / "sbt_0.13"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.11.4" % Test

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % Test

libraryDependencies +="junit" % "junit" % "4.11" % Test

libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier" % "1.0.0-M15",
  "io.get-coursier" %% "coursier-cache" % "1.0.0-M15",
  "io.get-coursier" % "sbt-coursier" % "1.0.0-M15" extra("sbtVersion" -> "0.13", "scalaVersion" -> "2.10")
)