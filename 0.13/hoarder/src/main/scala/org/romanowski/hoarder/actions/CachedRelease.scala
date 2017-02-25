package org.romanowski.hoarder.actions

import java.nio.file.{Files, Path}

import coursier.Dependency
import org.apache.ivy.core.settings.IvySettings
import org.romanowski.HoarderCommonSettings._
import org.romanowski.hoarder.core.HoarderEngine
import sbt.Def._
import sbt.Keys._
import sbt._


object CachedRelease extends HoarderEngine {

  type ArtifactsResolver = (ModuleID, Seq[Resolver]) => Map[String, URL]

  val incArtifact = SettingKey[Artifact]("incArtifact", "incArtifact")
  val incPublishedArtifact = TaskKey[(Artifact, File)]("incPublishedArtifact", "incPublishedArtifact")
  val incCachedRes = TaskKey[Unit]("incCachedRes", "incCachedRes")
  val resolveIncArtifacts = TaskKey[ArtifactsResolver]("resolveIncArtifacts")
  val allToLoad = TaskKey[Seq[CacheSetup]]("allToLoad", "Stash results of your current compilation")
  val doToLoadData = TaskKey[CacheSetup]("doToLoad", "Stash results of your current compilation")
  val loadRelease = InputKey[Unit]("loadRelease", "Load cached release from specified version.")
  val failOnMissing = SettingKey[Boolean]("failOnMissing", "Fail task if there are missing arifacts")

  private implicit class IncAwareConfiguration(configuration: Configuration){
    def inc = configuration.name + "-inc"
  }

  val IncConfig = Configurations.config("IncConfig")

  private def doPackageIncArtefact = Def.task[File]{
    val txtFile = exportCacheTaskImpl(projectSetupFor.value,
        compileIncremental.value,
        crossTarget.value.toPath.resolve("inc-artifact"),
        zipClasses = false
      ).resolve(analysisCacheFileName).toFile
    // TODO #6 remove zipping when supported in core
    val zipFile = txtFile.getParentFile / "analysis.zip"
    IO.zip(Seq(txtFile -> analysisCacheFileName), zipFile)
    zipFile
  }

  def doIncArtifact = Def.setting {
    Artifact(
      name = name.value,
      `type` = configuration.value.inc,
      extension = "zip",
      classifier = configuration.value.inc
    )
  }

  def createArifactPerConfiguration = Seq(
    incArtifact := doIncArtifact.value,
    incPublishedArtifact := (doIncArtifact.value, doPackageIncArtefact.value),
    doToLoadData := projectSetupFor.value
  )

  def settings = inConfig(Compile)(createArifactPerConfiguration) ++
    inConfig(Test)(createArifactPerConfiguration) ++ Seq(
      artifacts += incArtifact.in(Compile).value,
      artifacts += incArtifact.in(Test).value,
      packagedArtifacts += incPublishedArtifact.in(Compile).value,
      packagedArtifacts += incPublishedArtifact.in(Test).value,
      loadRelease := doLoadCache.evaluated,
      allToLoad := Seq(doToLoadData.in(Compile).value, doToLoadData.in(Test).value),
      resolveIncArtifacts := resolveIncArtifactsTask.value,
      failOnMissing := true
  )

  private val jarName = "binaries.jar"


  override protected def extractBinaries(cacheLocation: Path, cacheSetup: CachedRelease.CacheSetup): Set[File] = {
    import cacheSetup._
    val jar = cacheLocation.resolve(jarName)
    val outputDir = classesRoot.toFile

    if (Files.exists(jar)) {
      cleanOutput(outputDir, cleanOutputMode)
      IO.unzip(jar.toFile, outputDir, filter = (name: String) => name.endsWith(".class") , preserveLastModified = true)
    } else Set.empty
  }

  val versionParser = {
    import sbt.complete.Parser._
    import sbt.complete.Parsers._

    Space ~> token(StringBasic, "<version>")
  }

  def resolveIncArtifactsTask = Def.task[ArtifactsResolver]{
    CoursierResolver(streams.value.log, scalaVersion.value, scalaBinaryVersion.value).resolve _
  }

  def doLoadCache = Def.inputTask {
    val version = versionParser.parsed
    val baseModule = (organization.value %% name.value % version).intransitive()
    val tmpLocation = streams.value.cacheDirectory.toPath
    val artifacts = resolveIncArtifacts.value(baseModule, publishTo.value.toSeq ++ externalResolvers.value)

    streams.value.log.info(s"Got $artifacts")

    def resolveArtifact(configuration: String): Option[URL] = {
      def failureMessage = s"Artifact $baseModule for configuration $configuration not found!"

      artifacts.get(configuration) match {
        case None if failOnMissing.value =>
          throw new RuntimeException(failureMessage)
        case None =>
          streams.value.log.error(failureMessage)
          None
        case success =>
          success
      }
    }

    for{
      cacheData <- allToLoad.value
      incArtifactUrl <- resolveArtifact(cacheData.configuration.name)
      binaryArtifactUrl <- resolveArtifact(cacheData.configuration.inc)
    } {
      val destDir = tmpLocation.resolve(cacheData.relativeCacheLocation).toFile
      IO.download(incArtifactUrl, new File(destDir, jarName))
      IO.unzipURL(incArtifactUrl, destDir)
      importCacheTaskImpl(cacheData, tmpLocation)
    }
  }
}
