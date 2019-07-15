package com.ltrojanowski.testaffected

import sbt.SettingKey

trait Settings {

  val ignoredFilesOrDirs =
    SettingKey[Seq[String]]("ignored-files-or-dirs", "Specify relative paths of files or directories to exclude")
}
