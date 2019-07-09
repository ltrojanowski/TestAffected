package com.ltrojanowski.testaffected

import sbt.Keys._
import sbt.internal.{BuildStructure, LoadedBuildUnit}
import sbt.{Def, _}

object TestAffected extends AutoPlugin {

  override def trigger = allRequirements

  override def buildSettings: Seq[Def.Setting[_]] = Seq(
    commands ++= Seq(
      testAffectedCommand
    )
  )

  val testAffectedCommand = Command.command("testAffected")(testAffected)

  private[this] def testAffected(s: State): State = {
    implicit val extracted: Extracted = Project extract s

    val currentBuildUri: URI = extracted.currentRef.build

    val buildStructure: BuildStructure           = extracted.structure
    val buildUnitsMap: Map[URI, LoadedBuildUnit] = buildStructure.units
    val currentBuildUnit: LoadedBuildUnit        = buildUnitsMap(currentBuildUri)

    val projectsMap: Map[String, ResolvedProject] = currentBuildUnit.defined

    val projects: Seq[ResolvedProject] = projectsMap.values.toVector

    val projectsByPath: Map[String, ResolvedProject] = projects.map(p => (p.base.getAbsolutePath, p)).toMap

    implicit val projectsContext: ProjectsContext = ProjectsContext(projects, projectsMap, projectsByPath)

    val currentProject   = extracted.currentProject
    val currentProjectId = currentProject.id

    val logger            = extracted.get(sLog)
    val workingDir        = file(".").getAbsoluteFile
    val commandRunner     = new CommandRunnerImpl(workingDir, logger)
    val gitClient         = new GitClientImpl(logger, commandRunner)
    val dependencyTracker = new DependencyTrackerImpl(logger)
    val affectedModules   = new AffectedModuleDetectorImpl(logger, gitClient, dependencyTracker)

    logger.info(s"workingDir: $workingDir")
    logger.info(s"projectsByPath: $projectsByPath")

    val modulesToTest: Option[Set[ResolvedProject]] = affectedModules.findAffectedModules()

    val shouldTestEverything = modulesToTest match {
      case None => logger.warn("Failed to obtain git diff. Will test everything."); true
      case Some(toTest) if toTest.contains(currentProject) => {
        logger.info(s"Affected modules contain root module:\n  - ${toTest
          .map(
            p =>
              if (p.id == currentProjectId) {
                s"p.id <- (root project)"
              } else {
                p.id
              }
          )
          .mkString("\n  - ")}\n\n Will test everything.")
        true
      }
      case Some(toTest) => logger.info(s"Modules to test:\n  - ${toTest.map(_.id).mkString("\n  - ")}"); false
    }

    if (shouldTestEverything) {
      Command.process("test", s)
    } else {
      val modules = modulesToTest.get
      modules.map(_.id).foldLeft(Command.process(s"; project $currentProjectId; ", s)) {
        case (state, moduleId) => Command.process(s"; project $moduleId; test", state)
      }
    }
  }

}
