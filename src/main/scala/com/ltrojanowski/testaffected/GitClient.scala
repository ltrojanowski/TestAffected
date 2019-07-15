package com.ltrojanowski.testaffected

import java.io.{BufferedReader, File, InputStreamReader}
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import sys.process._

import sbt.util.Logger

import scala.collection.JavaConverters._

trait CommandRunner {
  def execute(command: String): List[String]

  implicit class StringCommandRunner(command: String) {
    def runCommand(): List[String] = execute(command)
  }
}

trait GitClient {

  def finedChangedFilesSince(sha: String, top: String, includeUncommitted: Boolean = false): List[String]

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
    val response = Process(command).lineStream.toList
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

  override def finedChangedFilesSince(sha: String, top: String, includeUncommitted: Boolean): List[String] = {
    val pathToGitRepo = PATH_TO_GIT_REPO.runCommand().headOption
    val excludeSuffix = ignoredFilesOrDirs.map(path => s"'(exclude)$path'").mkString(" ")
    val changedFiles = (if (includeUncommitted) {
                          s"$CHANGED_FILES_CMD_PREFIX $sha $excludeSuffix"
                        } else {
                          s"$CHANGED_FILES_CMD_PREFIX $top $sha $excludeSuffix"
                        })
      .runCommand()
      .flatMap(_.split(File.separator).headOption)
      .flatMap(relativePath => pathToGitRepo.map(_ + File.separator + relativePath))
    logger.info(s"Git diff found the following files changed:\n${changedFiles.mkString(" - ", "\n - ", "")}")
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
}
