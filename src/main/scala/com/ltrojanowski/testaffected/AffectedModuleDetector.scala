package com.ltrojanowski.testaffected

import sbt.util.Logger
import sbt.{Project, ResolvedProject}

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
    val changedModules = changedFiles.map(filePaths =>
      filePaths.flatMap(filePathToProject).toSet
    )

    changedModules.map(dependencyTracker.findAllAffected)
  }

  def filePathToProject(filePath: String): Option[ResolvedProject] = {
    import projectsContext._
    for {
      baseProjectPath <- filePath
        .split("/src")
        .headOption
        .fold(projectsByPath.find(pair => filePath.startsWith(pair._1)).map(_._1))(Some(_))
      //    logger.info(s"baseProjectPath: $baseProjectPath")
      r <- projectsByPath.get(baseProjectPath)
    } yield r
  }
}