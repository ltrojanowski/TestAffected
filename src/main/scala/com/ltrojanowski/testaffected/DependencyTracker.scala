package com.ltrojanowski.testaffected

import sbt.ResolvedProject
import sbt.util.Logger

import scala.collection.mutable

final case class Edge[A](from: A, to: A) {
  def reverte = Edge(to, from)

  override def toString: String = s"Edge(from: $from, to: $to)"
}

trait DependencyTracker {

  def findAllAffected(p: Set[ResolvedProject]): Set[ResolvedProject]

}

class DependencyTrackerImpl(logger: Logger)(implicit projectsContext: ProjectsContext) extends DependencyTracker {

  lazy val edges: Seq[Edge[ResolvedProject]] = {
    import projectsContext._
    projects.flatMap(
      from => from.dependencies.flatMap(dep => projectsMap.get(dep.project.project)).map(to => Edge(from, to))
    )
  }

  override def findAllAffected(p: Set[ResolvedProject]): Set[ResolvedProject] = {
    val affected: mutable.Set[ResolvedProject] = mutable.Set.empty
    val reverted                               = edges.map(_.reverte)
    logger.info(s"reversed edges: $reverted")
    def addParentProject(p: ResolvedProject): Unit = {
      if (affected.add(p)) {
        reverted.filter(_.from.equals(p)).map(_.to).foreach(addParentProject)
      }
    }
    p.foreach { addParentProject }
    affected.toSet
  }
}
