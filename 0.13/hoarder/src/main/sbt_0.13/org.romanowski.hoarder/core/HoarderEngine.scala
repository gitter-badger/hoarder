/*
 * Hoarder - Cached compilation plugin for sbt.
 * Copyright 2016 - 2017, Krzysztof Romanowski
 * This software is released under the terms written in LICENSE.
 */

package org.romanowski.hoarder.core

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, Path}

import sbt.compiler.{IC, MixedAnalyzingCompiler}
import sbt.inc.MappableFormat
import sbt.internal.inc.AnalysisMappers
import sbt.{PathFinder, _}
import xsbti.compile.SingleOutput


class HoarderEngine extends HoarderEngineCommon {

  type CompilationResult = IC.Result
  type PreviousCompilationResult = Compiler.PreviousAnalysis


  protected override def exportCacheTaskImpl(setup: CacheSetup,
                                             result: CompilationResult,
                                             globalCacheLocation: Path,
                                             zipClasses: Boolean = true
                                            ): Path = {
    import setup._
    val cacheLocation = globalCacheLocation.resolve(relativeCacheLocation)

    if (Files.exists(cacheLocation)) {
      if (overrideExistingCache) IO.delete(cacheLocation.toFile)
      else new IllegalArgumentException(s"Cache already exists at $cacheLocation.")
    }

    Files.createDirectories(cacheLocation)

    val mapper = createMapper(setup)
    val fos = Files.newBufferedWriter(cacheLocation.resolve(analysisCacheFileName), Charset.forName("UTF-8"))
    try {
      new MappableFormat(mapper).write(fos, result.analysis, result.setup)
    } finally fos.close()

    val outputPath = ouputForProject(result.setup).toPath

    if (zipClasses) {
      val classes = (PathFinder(classesRoot.toFile) ** "*.class").get
      val classesToZip = classes.map { classFile =>
        val mapping = outputPath.relativize(classFile.toPath).toString
        classFile -> mapping
      }

      IO.zip(classesToZip, cacheLocation.resolve(classesZipFileName).toFile)
    }
    cacheLocation
  }

  protected override def importCacheTaskImpl(cacheSetup: CacheSetup,
                                             globalCacheLocation: Path): Option[PreviousCompilationResult] = {
    import cacheSetup._
    val cacheLocation = globalCacheLocation.resolve(relativeCacheLocation)

    val from = cacheLocation.resolve(analysisCacheFileName)
    val mapper = createMapper(cacheSetup)

    if (Files.exists(from) && extractBinaries(cacheLocation, cacheSetup).nonEmpty) {
      val ios = Files.newBufferedReader(from, Charset.forName("UTF-8"))

      val (analysis, setup) = try {
        new MappableFormat(mapper).read(ios)
      } finally ios.close()

      val store = MixedAnalyzingCompiler.staticCachedStore(analysisFile)
      store.set(analysis, setup)

      Some(Compiler.PreviousAnalysis(analysis, Some(setup)))
    } else None
  }

  private def createMapper(projectSetup: CacheSetup): AnalysisMappers = {
    import projectSetup._
    new SbtAnalysisMapper(classesRoot, sourceRoots.map(_.toPath), projectRoot, classpath)
  }

  private def ouputForProject(setup: CompileSetup): File = setup.output match {
    case s: SingleOutput =>
      s.outputDirectory()
    case _ =>
      fail("Cannot use cache in multi-output situation")
  }
}
