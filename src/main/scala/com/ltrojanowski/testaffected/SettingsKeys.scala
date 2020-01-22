package com.ltrojanowski.testaffected

import sbt.{ResolvedProject, SettingKey, TaskKey}
import sbt.Keys._

trait SettingsKeys {

  lazy val ignoredFilesOrDirs =
    SettingKey[Seq[String]]("ignored-files-or-dirs", "Specify relative paths of files or directories to exclude")

  lazy val diffAffectedProjects =
    TaskKey[Unit]("diffAffectedProjects", "Returns the set of projects affected by the git diff")

}
