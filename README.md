# TestAffected
An sbt plugin to only run tests in modules which could have been affected by your changes

In a project with the following strucutre

                  +========+
              +---+module A+----+
              |   +========+    |
              v                 v
         +====+===+         +---+----+
       +-+module B+--+      |module C|
       | +========+  |      +--------+
       v             v
    +--+-----+   +===+====+
    |module D|   |module E|
    +--------+   +========+
A change in module E would only run tests in module E, B and A

To add the plugin to your project add the following lines

    // plugins.sbt
    resolvers += Resolver.bintrayIvyRepo("ltrojanowski", "test-affected")
    addSbtPlugin("com.ltrojanowski" % "testaffected" % "0.2.4")


If you want to exclude some files from being taken into account by test affected when edited you can do so by setting the `ignoredFilesOrDirs` key.

    ignoredFilesOrDirs := Seq(
      "*.sbt",
      "project/*",
      "jenkins/*"
    )

In your jenkins test stage remember to also check out the target branch, since the plugin works by running a git diff. This is only possible when both branches have been checked out.

    stage('Test') {
                if (isMaster()) {
                    utils.sbt "${modulePrefix}test"
                } else {
                    if (isPullRequest()) {
                        sh "git fetch origin ${env.CHANGE_TARGET}"
                    } else {
                        sh "git fetch origin master"
                    }
                    utils.sbt "${modulePrefix}testAffected HEAD FETCH_HEAD"
                }
            }
