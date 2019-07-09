package com.ltrojanowski.testaffected

import sbt.util.Logger
import sbt.{Project, ResolvedProject}
import scala.collection.immutable.Seq

sealed trait ProjectSubset
case object DEPENDENT_PROJECTS extends ProjectSubset
case object CHANGED_PROJECTS extends ProjectSubset
case object ALL_AFFECTED_PROJECTS extends ProjectSubset

abstract class AffectedModuleDetector {

  def shouldInclude(project: Project): Boolean

}

class AcceptAll extends AffectedModuleDetector {

  override def shouldInclude(project: Project) = true

}

class AffectedModuleDetectorImpl(
    logger: Logger,
    gitClient: GitClient,
    dependencyTracker: DependencyTracker
)(implicit projectsContext: ProjectsContext) {

  def findAffectedModules(): Option[Set[ResolvedProject]] = { // if None either git failed or is not in this repo
    val lastMergeSha = gitClient.findPreviousMergeCL()
    val changedFiles = lastMergeSha.map(sha => gitClient.finedChangedFilesSince(sha, includeUncommitted = true))

    logger.info(s"changed files: $changedFiles")
    val changedModules = changedFiles.map(filePaths => filePaths.flatMap(filePathToProject).toSet)

    changedModules.map(dependencyTracker.findAllAffected)
  }

  private val projectPaths = projectsContext.projectsByPath.keys

  def filePathToProject(filePath: String): Option[ResolvedProject] = {
    import projectsContext._

    val projectPath = projectPaths.filter(filePath.startsWith).toSeq match {
      case Nil                => None
      case paths: Seq[String] => Some(paths.maxBy(_.length))
    }

    projectPath.flatMap(projectsByPath.get)
  }
}
