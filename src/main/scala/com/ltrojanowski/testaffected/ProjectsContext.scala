package com.ltrojanowski.testaffected

import sbt.ResolvedProject

case class ProjectsContext(
  projects: Seq[ResolvedProject],
  projectsMap: Map[String, ResolvedProject],
  projectsByPath: Map[String, ResolvedProject]
)
