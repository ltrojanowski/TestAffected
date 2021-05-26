Copyright © 2021 Łukasz Trojanowski

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

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
