package com.ltrojanowski.testaffected

import sbt.{InputKey, ResolvedProject, SettingKey}

trait SettingsKeys {

  lazy val ignoredFilesOrDirs =
    SettingKey[Seq[String]]("ignored-files-or-dirs", "Specify relative paths of files or directories to exclude")

  lazy val diffAffectedProjects =
    InputKey[(ResolvedProject, Set[ResolvedProject])]("diffAffectedProjects", "Returns the set of projects affected by the git diff")

}
