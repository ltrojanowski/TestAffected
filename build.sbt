libraryDependencies ++= Seq(
  "org.scalactic" %% "scalactic" % "3.0.5",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

lazy val root = (project in file("."))
  .settings(
    sbtPlugin := true,
    name := "TestAffected",
    licenses += ("MIT", url("https://raw.githubusercontent.com/ltrojanowski/licences/master/MIT_2019")),
    version := "0.1.6",
    organization := "com.ltrojanowski",
    publishMavenStyle := false,
    description := "A plugin to test only modules affected by your changes for a faster CICD pipeline",
    bintrayRepository := "test-affected",
    bintrayOrganization in bintray := None
  )
