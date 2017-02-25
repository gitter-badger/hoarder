package org.romanowski.hoarder.actions

import java.io.File

import coursier.Attributes
import sbt.{Logger, ModuleID, Resolver, URL}

case class CoursierResolver(log: Logger, scalaVersion: String, scalaBinaryVersion: String) {
  lazy val ivyHome = sys.props.getOrElse(
    "ivy.home",
    new File(sys.props("user.home")).toURI.getPath + ".ivy2"
  )

  lazy val sbtIvyHome = sys.props.getOrElse(
    "sbt.ivy.home",
    ivyHome
  )

  lazy val ivyProperties = Map(
    "ivy.home" -> ivyHome,
    "sbt.ivy.home" -> sbtIvyHome
  ) ++ sys.props

  def resolve(sbtModule: ModuleID, resolvers: Seq[Resolver]): Map[String, URL] = {
    import coursier._

    val Seq((_, dep)) = FromSbt.dependencies(sbtModule, scalaVersion, scalaBinaryVersion, "jar")

    val start = Resolution(Set(dep.copy(configuration = ""), dep.copy(configuration = "test")))

    val repos = resolvers.flatMap(r => FromSbt.repository(r, ivyProperties, log, None))

    val fetch = Fetch.from(repos, Cache.fetch())

    val resolution = start.process.run(fetch).run

    resolution.dependencyArtifacts.collect {
      case (artifactDependency, artifact) if artifactDependency.module == dep.module =>
        val config = artifact.attributes.classifier match {
          case "" => "compile"
          case "tests" => "test"
          case other => other
        }

        config -> new URL(artifact.url)
    }(collection.breakOut)
  }
}
