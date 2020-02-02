package com.ltrojanowski.testaffected

object Glob2Regex {

  def glob2Regex(glob: String): String = {
    val line = glob.trim().stripPrefix("*").stripSuffix("*")
    line
      .foldLeft((false, "")) {
        case ((escaping, acc), char) =>
          (escaping, char) match {
            case (true, '*')  => (false, acc + "\\*")
            case (false, '*') => (false, acc + ".*")
            case (true, '?')  => (false, acc + "\\?")
            case (false, '?') => (false, acc + ".")
            case (_, escapeChar) if Seq('.', '(', ')', '+', '|', '^', '$', '@', '%').contains(escapeChar) =>
              (false, acc + "\\" + escapeChar)
            case (false, '\\') => (true, acc)
            case (true, '\\')  => (true, acc + "\\\\")
            case (_, char)     => (false, acc + char)
          }
      }
      ._2
  }

}
