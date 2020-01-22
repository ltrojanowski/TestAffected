package com.ltrojanowski.testaffected

import sbt.{ResolvedProject, SettingKey}

trait SettingsKeys {

  lazy val ignoredFilesOrDirs =
    SettingKey[Seq[String]]("ignored-files-or-dirs", "Specify relative paths of files or directories to exclude")

  lazy val diffAffectedProjects = inputKey[Seq[ResolvedProject]]("")
}
