package com.ltrojanowski.testaffected

import java.io.File

import sbt.util.Logger

import scala.sys.process._

trait CommandRunner {
  def execute(command: String): List[String]

  implicit class StringCommandRunner(command: String) {
    def runCommand(): List[String] = execute(command)
  }
}

trait GitClient {

  def findChangedFilesSince(sha: String, top: String, includeUncommitted: Boolean = false): List[String]

  def findPreviousMergeCL(): Option[String]

  def findBranchingPointFromMaster(
      branchToCompare: Option[String] = None,
      targetBranch: Option[String]    = None
  ): Option[String]

  def findHeadOfBranch(branchToCompare: Option[String]): Option[String]
}

class CommandRunnerImpl(workingDir: File, logger: Logger) extends CommandRunner {

  override def execute(command: String): List[String] = {
    logger.info(s"running command $command")
    val response = Process(command.split(" ").toList).lineStream.toList
    logger.info(s"Response: ${response.mkString(System.lineSeparator())}")
    response
  }

}

class GitClientImpl(
    private val logger: Logger,
    private val commandRunner: CommandRunner,
    private val ignoredFilesOrDirs: Seq[String]
) extends GitClient {

  import GitClientImpl._
  import commandRunner._
  import scala.util.matching._

  private def getGitVersion() = {
    GIT_VERSION.runCommand().headOption.flatMap(_.split(" ").takeRight(1).headOption)
  }

  private def logIgnoredFilesOrDirs(ignoredFilesOrDirs: Seq[String]) = {
    if (ignoredFilesOrDirs.isEmpty) {
      logger.info(s"Git diff will not exclude any files")
    } else {
      logger.info(s"Git diff will exclude the following files:\n${ignoredFilesOrDirs.mkString(" - ", "\n - ", "")}")
    }
  }

  private def logChangedFiles(changedFiles: Seq[String]) = {
    if (changedFiles.isEmpty) {
      logger.info("Git diff didn't find any changed files")
    } else {
      logger.info(s"Git diff found the following files changed:\n${changedFiles.mkString(" - ", "\n - ", "")}")
    }
  }

  override def findChangedFilesSince(sha: String, top: String, includeUncommitted: Boolean): List[String] = {
    import Glob2Regex.glob2Regex
    val pathToGitRepo = PATH_TO_GIT_REPO.runCommand().headOption
    val excludeSuffix = ignoredFilesOrDirs
      .flatMap(ignoredFileOrDir => pathToGitRepo.map(path => s":(exclude)$path${File.separator}$ignoredFileOrDir"))
    logIgnoredFilesOrDirs(ignoredFilesOrDirs)
    val changedFiles = if (getGitVersion().exists(_ >= "1.9.5")) {
      (
        if (includeUncommitted) {
          s"$CHANGED_FILES_CMD_PREFIX $sha${excludeSuffix.mkString(" -- . ", " ", "")}"
        } else {
          s"$CHANGED_FILES_CMD_PREFIX $top $sha${excludeSuffix.mkString(" -- . ", " ", "")}"
        }
      ).runCommand()
        .flatMap(file => pathToGitRepo.map(path => s"$path${File.separator}$file"))
    } else {
      (
        if (includeUncommitted) {
          s"$CHANGED_FILES_CMD_PREFIX $sha"
        } else {
          s"$CHANGED_FILES_CMD_PREFIX $top $sha"
        }
      ).runCommand()
        .flatMap(file => pathToGitRepo.map(path => s"$path${File.separator}$file"))
        .filterNot(
          unfilteredFilePath =>
            ignoredFilesOrDirs
              .flatMap(relativeIgnoredFile => pathToGitRepo.map(path => s"$path${File.separator}$relativeIgnoredFile"))
              .exists(
                ignoredFile => new Regex(glob2Regex(ignoredFile)).findFirstIn(s"$unfilteredFilePath").isDefined
              )
        )
    }
    logChangedFiles(changedFiles)
    changedFiles
  }

  override def findPreviousMergeCL(): Option[String] = {
    PREV_MERGE_CMD
      .runCommand()
      .headOption
      .flatMap(
        _.split(" ").headOption
      )
  }

  override def findBranchingPointFromMaster(
      branchToCompare: Option[String] = None,
      targetBranch: Option[String]    = None
  ): Option[String] = {
    for {
      comparedBranch <- branchToCompare match {
        case None            => CURRENT_BRANCH_CMD.runCommand().headOption
        case b: Some[String] => identity(b)
      }
      branchingPoint <- s"$BRANCHING_FROM_TARGET_PREFIX ${targetBranch.getOrElse("master")} $comparedBranch"
        .runCommand()
        .headOption
    } yield branchingPoint
  }

  override def findHeadOfBranch(branchToCompare: Option[String]): Option[String] = {
    s"$HEAD_OF_BRANCH_PREFIX ${branchToCompare.getOrElse("HEAD")}"
      .runCommand()
      .headOption
      .flatMap(
        _.split(" ").headOption
      )
  }

}

object GitClientImpl {
  val PATH_TO_GIT_REPO             = "git rev-parse --show-toplevel"
  val PREV_MERGE_CMD               = "git log -1 --merges --oneline"
  val CURRENT_BRANCH_CMD           = "git rev-parse --abbrev-ref HEAD"
  val BRANCHING_FROM_TARGET_PREFIX = s"git merge-base"
  val CHANGED_FILES_CMD_PREFIX     = "git diff --name-only"
  val HEAD_OF_BRANCH_PREFIX        = "git log -1 --oneline"
  val GIT_VERSION                  = "git --version"
}
