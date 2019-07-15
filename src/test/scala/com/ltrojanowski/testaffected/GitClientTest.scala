package com.ltrojanowski.testaffected

import java.io.File
import java.util.function.Supplier

import org.scalatest.{BeforeAndAfter, FlatSpec}

class GitClientTest extends FlatSpec with BeforeAndAfter {

  behavior of GitClientImpl.getClass.getSimpleName

  trait Fixture {
    val mockCommandRunner = new MockCommandRunner()
    val client = new GitClientImpl(
      logger        = new TestLogger(),
      commandRunner = mockCommandRunner,
      ???
    )
  }

  it should "findPreviousMergeCL() should return sha" in new Fixture {
    mockCommandRunner.addReply(
      GitClientImpl.PREV_MERGE_CMD,
      "abcdefghij (m/androidx-md, aosp/androidx-md) Merge blah blah into and"
    )
    assert(client.findPreviousMergeCL().contains("abcdefghij"))
  }

  it should "findMerge fail" in new Fixture {
    assert(client.findPreviousMergeCL().isEmpty)
  }

  it should "find changes since" in new Fixture {
    val changes = List(
      convertToFilePath("a", "b", "c.java"),
      convertToFilePath("d", "e", "f.java")
    )
    mockCommandRunner.addReply(
      s"${GitClientImpl.CHANGED_FILES_CMD_PREFIX} mySha",
      changes.mkString(System.lineSeparator())
    )
    assert(client.finedChangedFilesSince(sha = "mySha", includeUncommitted = true) == changes)
  }

  it should "find changes since empty" in new Fixture {
    assert(List.empty[String] == client.finedChangedFilesSince("foo"))
  }

  def convertToFilePath(list: String*): String = {
    list.toList.mkString(File.separator)
  }

  it should "find changes since two calls" in new Fixture {
    val changes = List(
      convertToFilePath("a", "b", "c.java"),
      convertToFilePath("d", "e", "f.java")
    )
    mockCommandRunner.addReply(
      s"${GitClientImpl.CHANGED_FILES_CMD_PREFIX} otherSha mySha",
      changes.mkString(System.lineSeparator())
    )
    assert(changes == client.finedChangedFilesSince(sha = "mySha", top = "otherSha"))
  }

  class MockCommandRunner extends CommandRunner {

    var replies = scala.collection.mutable.Map[String, List[String]]()

    def addReply(command: String, response: String): Unit = {
      replies(command) = response.split(System.lineSeparator()).toList
    }

    override def execute(command: String): List[String] = {
      replies.getOrElse(command, List.empty[String])
    }
  }

  class TestLogger extends xsbti.Logger {
    override def error(msg: Supplier[String]): Unit          = {}
    override def warn(msg: Supplier[String]): Unit           = {}
    override def info(msg: Supplier[String]): Unit           = {}
    override def debug(msg: Supplier[String]): Unit          = {}
    override def trace(exception: Supplier[Throwable]): Unit = {}
  }
}
