package com.ltrojanowski.testaffected

import sbt.util.Logger
import sbt.{Project, ResolvedProject}
import scala.collection.immutable.Seq

abstract class AffectedModuleDetector {

  def findAffectedModules(branchToCompare: Option[String]): Option[Set[ResolvedProject]]

}

class AffectedModuleDetectorImpl(
    logger: Logger,
    gitClient: GitClient,
    dependencyTracker: DependencyTracker
)(implicit projectsContext: ProjectsContext)
    extends AffectedModuleDetector {

  def findAffectedModules(branchToCompare: Option[String]): Option[Set[ResolvedProject]] = { // if None either git failed or is not in this repo
    val changedFiles = for {
      lastMergeSha    <- gitClient.findBranchingPointFromMaster(branchToCompare)
      headOfBranchSha <- gitClient.findHeadOfBranch(branchToCompare)
    } yield gitClient.finedChangedFilesSince(lastMergeSha, top = headOfBranchSha)

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
