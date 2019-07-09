package com.ltrojanowski.testaffected

import java.io.{BufferedReader, File, InputStreamReader}
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import sbt.util.Logger

import scala.collection.JavaConverters._

trait CommandRunner {
  def execute(command: String): List[String]

  implicit class StringCommandRunner(command: String) {
    def runCommand(): List[String] = execute(command)
  }
}

trait GitClient {

  def finedChangedFilesSince(sha: String, top: String = "HEAD", includeUncommitted: Boolean = false): List[String]

  def findPreviousMergeCL(): Option[String]

}

class CommandRunnerImpl(workingDir: File, logger: Logger) extends CommandRunner {

  override def execute(command: String): List[String] = {
    val parts = command.split("\\s")
    logger.info(s"running command $command")
    val proc = new ProcessBuilder(parts: _*)
      .directory(workingDir)
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(ProcessBuilder.Redirect.PIPE)
      .start()

    proc.waitFor(1, TimeUnit.MINUTES)
    val response = new BufferedReader(new InputStreamReader(proc.getInputStream))
      .lines()
      .collect(Collectors.toList[String]())
      .asScala
      .toList
    logger.info(s"Response: ${response.mkString(System.lineSeparator())}")
    response
  }

}

class GitClientImpl(private val logger: Logger, private val commandRunner: CommandRunner) extends GitClient {

  import GitClientImpl._
  import commandRunner._

  override def finedChangedFilesSince(sha: String, top: String, includeUncommitted: Boolean): List[String] = {
    val pathToGitRepo = PATH_TO_GIT_REPO.runCommand().headOption
    (if (includeUncommitted) {
       s"$CHANGED_FILES_CMD_PREFIX $sha"
     } else {
       s"$CHANGED_FILES_CMD_PREFIX $top $sha"
     })
      .runCommand()
      .flatMap(_.split(File.separator).headOption)
      .flatMap(relativePath => pathToGitRepo.map(_ + File.separator + relativePath))
  }

  override def findPreviousMergeCL(): Option[String] = {
    PREV_MERGE_CMD
      .runCommand()
      .headOption
      .flatMap(
        _.split(" ").headOption
      )
  }

}

object GitClientImpl {
  val PATH_TO_GIT_REPO         = "git rev-parse --show-toplevel"
  val PREV_MERGE_CMD           = "git log -1 --merges --oneline"
  val CHANGED_FILES_CMD_PREFIX = "git diff --name-only"
}
