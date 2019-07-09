package com.ltrojanowski.testaffected

import sbt.util.Logger
import sbt.{Project, ResolvedProject}
import scala.collection.immutable.Seq

abstract class AffectedModuleDetector {

  def findAffectedModules(): Option[Set[ResolvedProject]]

}

class AffectedModuleDetectorImpl(
    logger: Logger,
    gitClient: GitClient,
    dependencyTracker: DependencyTracker
)(implicit projectsContext: ProjectsContext)
    extends AffectedModuleDetector {

  def findAffectedModules(): Option[Set[ResolvedProject]] = { // if None either git failed or is not in this repo
    val lastMergeSha = gitClient.findPreviousMergeCL()
    val changedFiles = lastMergeSha.map(sha => gitClient.finedChangedFilesSince(sha, includeUncommitted = true))

    logger.info(s"changed files: $changedFiles")
    val changedModules = changedFiles.map(filePaths => filePaths.flatMap(filePathToProject).toSet)

    changedModules.map(dependencyTracker.findAllAffected)
  }

  private val projectPaths = projectsContext.projectsByPath.keys

  private def filePathToProject(filePath: String): Option[ResolvedProject] = {
    import projectsContext._

    val projectPath = projectPaths.filter(filePath.startsWith).toSeq match {
      case Nil                => None
      case paths: Seq[String] => Some(paths.maxBy(_.length))
    }

    projectPath.flatMap(projectsByPath.get)
  }
}
