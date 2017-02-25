/*
 * Hoarder - Cached compilation plugin for sbt.
 * Copyright 2016 - 2017, Krzysztof Romanowski
 * This software is released under the terms written in LICENSE.
 */

package org.romanowski.hoarder.core

import java.nio.charset.Charset

import sbt.{PathFinder, _}
import sbt.Keys._
import java.nio.file.{Files, Path, Paths}

import org.romanowski.HoarderCommonSettings._

trait HoarderEngineCommon {
  type CompilationResult
  type PreviousCompilationResult

  val analysisCacheFileName = "analysis.txt"
  val classesZipFileName = "classes.zip"

  case class CacheSetup(sourceRoots: Seq[File],
                        classpath: Classpath,
                        classesRoot: Path,
                        projectRoot: Path,
                        analysisFile: File,
                        relativeCacheLocation: Path,
                        overrideExistingCache: Boolean,
                        cleanOutputMode: CleanOutputMode,
                        configuration: Configuration
                       )

  protected def exportCacheTaskImpl(setup: CacheSetup,
                                    result: CompilationResult,
                                    globalCacheLocation: Path,
                                    zipClasses: Boolean = true): Path

  protected def importCacheTaskImpl(cacheSetup: CacheSetup, globalCacheLocation: Path): Option[PreviousCompilationResult]

  protected def projectSetupFor = Def.task[CacheSetup] {
      CacheSetup(
        sourceRoots = managedSourceDirectories.value ++ unmanagedSourceDirectories.value,
        classpath = externalDependencyClasspath.value,
        classesRoot = classDirectory.value.toPath,
        projectRoot = baseDirectory.value.toPath,
        analysisFile = (streams in compileIncSetup).value.cacheDirectory / compileAnalysisFilename.value,
        relativeCacheLocation = Paths.get(name.value).resolve(configuration.value.name),
        overrideExistingCache = overrideExistingCache.value,
        cleanOutputMode = cleanOutputMode.value,
        configuration = configuration.value
      )
    }

  protected def cleanOutput(outputDir: File, cleanOutputMode: CleanOutputMode) = {
    if (outputDir.exists()) {
      if (outputDir.isDirectory) {
        cleanOutputMode match {
          case CleanOutput =>
            if (outputDir.list().nonEmpty) IO.delete(outputDir)
          case FailOnNonEmpty =>
            if (outputDir.list().nonEmpty)
              throw new IllegalStateException(s"Output directory: $outputDir is not empty and cleanOutput is false")
          case CleanClasses =>
            val classFiles = PathFinder(outputDir) ** "*.class"
            IO.delete(classFiles.get)
        }
      } else throw new IllegalStateException(s"Output file: $outputDir is not a directory")
    }
  }

  protected def extractBinaries(cacheLocation: Path, cacheSetup: CacheSetup): Set[File] = {
    import cacheSetup._

    val classesZip = cacheLocation.resolve(classesZipFileName)
    val outputDir = classesRoot.toFile

      if (Files.exists(classesZip)) {
        cleanOutput(outputDir, cleanOutputMode)
      IO.unzip(classesZip.toFile, outputDir, preserveLastModified = true)
    } else Set.empty
  }
}