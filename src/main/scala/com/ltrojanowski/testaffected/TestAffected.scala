package com.ltrojanowski.testaffected

import sbt.Keys._
import sbt.internal.{BuildStructure, LoadedBuildUnit}
import sbt.{Def, _}

object TestAffected extends AutoPlugin with Settings {

  override def trigger = allRequirements

  override def buildSettings: Seq[Def.Setting[_]] = Seq(
    commands ++= Seq(
      testAffectedCommand
    )
  )

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    ignoredFilesOrDirs in Global := Seq()
  )

  val testAffectedCommand = Command.args(
    "testAffected",
    "Tests all modules affected by your changes. The changes are resolved using a git diff."
  )(testAffected)

  private def extractArgs(args: Seq[String]): (Option[String], Option[String]) = {
    args match {
      case branchToCompare :: targetBranch :: Nil => (Some(branchToCompare), Some(targetBranch))
      case branchToCompare :: Nil                 => (Some(branchToCompare), None)
      case Nil                                    => (None, None)
    }
  }

  private[this] def testAffected(s: State, args: Seq[String]): State = {

    val (branchToCompare, targetBranch) = extractArgs(args)

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

    val modulesToTest: Option[Set[ResolvedProject]] = affectedModules.findAffectedModules(branchToCompare, targetBranch)

    val shouldTestEverything = modulesToTest match {
      case None                           => logger.warn("Failed to obtain git diff. Will test everything."); true
      case Some(toTest) if toTest.isEmpty => logger.info("No modules require testing."); false
      case Some(toTest) if toTest.contains(currentProject) => {
        logger.info(s"Affected modules contain root module:\n  - ${toTest
          .map(
            p =>
              if (p.id == currentProjectId) {
                s"${p.id} <- (root project)"
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
      MainLoop.processCommand(Exec("test", None), s)
    } else {
      val modules = modulesToTest.get
      modules.map(_.id).foldLeft(MainLoop.processCommand(Exec(s"; project $currentProjectId; ", None), s)) {
        case (state, moduleId) => MainLoop.processCommand(Exec(s"; project $moduleId; test", None), state)
      }
    }
  }

}
