import scala.util.matching.Regex
  * gitHeader       ::= "diff --git " fromGitFilename toGitFilename newline
  * fromGitFilename ::= """(?:"?a/)([&#94;*&%\t\n\r\f]+)(?:"? "?b/)"""
  * toGitFilename   ::= filename
  * filename        ::= """(?:"?)([&#94;*&%\t\n\r\f]+)(?:"?)"""
    "diff --git " ~> fromGitFilename ~ toGitFilename <~ newline ^^ { case fromGitFilenameRegex(f1) ~
      filenameRegex(f2) => (f1, f2) }
      { _ getOrElse ModifiedFile }
  def fromGitFilename: Parser[String] = fromGitFilenameRegex
  def toGitFilename: Parser[String] = filename
  def filename: Parser[String] = filenameRegex
  private val fromGitFilenameRegex: Regex = """(?:"?a/)([^*&%\t\n\r\f]+)(?:"? "?b/)""".r
  private val filenameRegex: Regex = """(?:"?)([^*&%\t\n\r\f]+)(?:"?)""".r
