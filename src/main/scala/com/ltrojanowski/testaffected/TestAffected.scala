package com.ltrojanowski.testaffected

import sbt.Keys._
import sbt.internal.{BuildStructure, LoadedBuildUnit}
import sbt.{Def, _}
import complete.DefaultParsers._

object TestAffected extends AutoPlugin with ArgsExtractors {
  object autoImport extends SettingsKeys
  import autoImport._

  override def trigger = allRequirements

  override def buildSettings: Seq[Def.Setting[_]] = Seq(
    commands ++= Seq(
      testAffectedCommand,
      inDiffAffectedExecuteCommand
    ),
    diffAffectedProjects := diffAffectedProjectsTask.evaluated
  )

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    ignoredFilesOrDirs in Global := Seq()
  )

  val testAffectedCommand = Command.args(
    "testAffected",
    "Tests all modules affected by your changes. The changes are resolved using a git diff."
  )(testAffected)

  val diffAffectedProjectsTask = Def.inputTask {
    val args: Seq[String]    = spaceDelimited("<args>").parsed
    val s                    = state.value
    val extracted: Extracted = Project extract s
    val logger               = extracted.get(sLog)

    args foreach { arg =>
      logger.info(arg)
    }
    val currentProject   = extracted.currentProject
    val currentProjectId = currentProject.id
    val modulesToTest = for {
      extractedBranches               <- extractBranches(args)
      (branchToCompare, targetBranch) = extractedBranches
      modulesToTest <- affectedProjectReferences(extracted, branchToCompare, targetBranch)
        .toRight[Throwable](new Throwable("Failed to fetch affected projects"))
    } yield modulesToTest

    modulesToTest match {
      case Right(affectedProjects) => {
        logger.info(affectedProjects.toString)
        (currentProject, affectedProjects)
      }
      case Left(e) => {
        logger.error(e.getMessage)
        (currentProject, Set.empty[ResolvedProject])
      }
    }
  }

  val inDiffAffectedExecuteCommand = Command.args(
    "inDiffAffected",
    "Executes a command in all modules affected by "
  )(inDiffAffectedExecute)

  private def affectedProjectReferences(
      extracted: Extracted,
      branchToCompare: Option[String],
      targetBranch: Option[String]
  ): Option[Set[ResolvedProject]] = {

    val currentBuildUri: URI = extracted.currentRef.build

    val buildStructure: BuildStructure           = extracted.structure
    val buildUnitsMap: Map[URI, LoadedBuildUnit] = buildStructure.units
    val currentBuildUnit: LoadedBuildUnit        = buildUnitsMap(currentBuildUri)

    val projectsMap: Map[String, ResolvedProject] = currentBuildUnit.defined

    val projects: Seq[ResolvedProject] = projectsMap.values.toVector

    val projectsByPath: Map[String, ResolvedProject] = projects.map(p => (p.base.getAbsolutePath, p)).toMap

    implicit val projectsContext: ProjectsContext = ProjectsContext(projects, projectsMap, projectsByPath)

    val logger        = extracted.get(sLog)
    val workingDir    = file(".").getAbsoluteFile
    val commandRunner = new CommandRunnerImpl(workingDir, logger)
    val gitClient =
      new GitClientImpl(logger, commandRunner, extracted.get(ignoredFilesOrDirs))
    val dependencyTracker = new DependencyTrackerImpl(logger)
    val affectedModules   = new AffectedModuleDetectorImpl(logger, gitClient, dependencyTracker)

    affectedModules.findAffectedModules(branchToCompare, targetBranch)
  }

  private[this] def testAffected(s: State, args: Seq[String]): State = {

    val extractedBranches = extractBranches(args)

    val extracted: Extracted = Project extract s
    val logger               = extracted.get(sLog)
    val modulesToTest: Either[Throwable, Set[ResolvedProject]] = for {
      extractedBranches               <- extractBranches(args)
      (branchToCompare, targetBranch) = extractedBranches
      affectedProjects <- affectedProjectReferences(extracted, branchToCompare, targetBranch)
        .toRight[Throwable](new Throwable("Failed to fetch affected projects"))
    } yield affectedProjects
    val currentProject   = extracted.currentProject
    val currentProjectId = currentProject.id

    val shouldTestEverything = modulesToTest match {
      case Left(_)                         => logger.warn("Failed to obtain git diff. Will test everything."); true
      case Right(toTest) if toTest.isEmpty => logger.info("No modules require testing."); false
      case Right(toTest) if toTest.contains(currentProject) => {
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
      case Right(toTest) => logger.info(s"Modules to test:\n  - ${toTest.map(_.id).mkString("\n  - ")}"); false
    }

    if (shouldTestEverything) {
      MainLoop.processCommand(Exec("test", None), s)
    } else {
      val modules =
        modulesToTest.getOrElse(throw new IllegalStateException("Reached unreachable state. You are on your own now"))
      modules.map(_.id).foldLeft(MainLoop.processCommand(Exec(s"; project $currentProjectId; ", None), s)) {
        case (state, moduleId) => MainLoop.processCommand(Exec(s"; project $moduleId; test", None), state)
      }
    }
  }

  private[this] def inDiffAffectedExecute(s: State, args: Seq[String]): State = {
    // inDiffAffected kiuhkjh2354 6lj34ht89 execute [rest of command]
    val extracted: Extracted = Project extract s
    val logger               = extracted.get(sLog)

    val currentProject   = extracted.currentProject
    val currentProjectId = currentProject.id

    val modulesToTest = for {
      extractedBranchesAndCommand              <- extractBranchesAndCommand(args)
      (branchToCompare, targetBranch, command) = extractedBranchesAndCommand
      modulesToTest <- affectedProjectReferences(extracted, branchToCompare, targetBranch)
        .toRight[Throwable](new Throwable("Failed to fetch affected projects"))
    } yield (modulesToTest, command.mkString(" "))

    modulesToTest match {
      case Right((projectsToTest, commandToRun)) =>
        projectsToTest
          .map(_.id)
          .foldLeft(MainLoop.processCommand(Exec(s"; project $currentProjectId; ", None), s)) {
            case (state, moduleId) => MainLoop.processCommand(Exec(s"; project $moduleId; $commandToRun", None), state)
          }
      case Left(e) => {
        logger.error(e.getMessage)
        s
      }
    }
  }

}
