package com.ltrojanowski.testaffected

trait ArgsExtractors {
  def extractBranches(args: Seq[String]): Either[Throwable, (Option[String], Option[String])] = {
    args match {
      case branchToCompare :: targetBranch :: Nil => Right((Some(branchToCompare), Some(targetBranch)))
      case branchToCompare :: Nil                 => Right((Some(branchToCompare), None))
      case Nil                                    => Right((None, None))
      case _                                      => Left(new Throwable("Too many arguments for method"))
    }
  }

  def extractBranchesAndCommand(
      args: Seq[String]
  ): Either[Throwable, (Option[String], Option[String], Seq[String])] = {
    args match {
      case _ :: _ :: "execute" :: Nil | _ :: "execute" :: Nil | "execute" :: Nil =>
        Left(new Throwable("missing command to run in affected projects"))
      case branchToCompare :: "execute" :: tail => Right((Some(branchToCompare), None, tail))
      case "execute" :: tail                    => Right(None, None, tail)
      case branchToCompare :: targetBranch :: "execute" :: tail =>
        Right((Some(branchToCompare), Some(targetBranch), tail))
      case _ => Left(new Throwable("missing hashes of branches to merge into and working branch"))
    }
  }
}
