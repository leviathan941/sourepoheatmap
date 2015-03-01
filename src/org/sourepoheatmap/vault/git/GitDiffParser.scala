  * @see [[https://www.kernel.org/pub/software/scm/git/docs/git-diff.html Git diff man]]
private[git] object GitDiffParser extends RegexParsers {
  case class FileDiff(oldFile: String, newFile: String, fileChange: FileChange, chunks: List[ChangeChunk])
    override def toString = "@@ -%d,%d +%d,%d @@".format(oldStartLine, oldLineNumber, newStartLine, newLineNumber)
  def allDiffs: Parser[List[FileDiff]] = rep1(gitDiff)
  def gitDiff: Parser[FileDiff] = gitDiffHeader ~ extendedHeader ~ opt(unifiedDiffHeader ~> diffChunks) ^^
    { case files ~ change ~ chunks => FileDiff(files._1, files._2, change, chunks getOrElse Nil)}
    opt(modeChanged) ~ opt(similarity) ~> opt(copiedFile | renamedFile | deletedFile | newFile) <~ index ^^
  def copiedFile: Parser[CopiedFile] = "copy from " ~> filename ~
  def renamedFile: Parser[RenamedFile] = "rename from " ~> filename ~
  def deletedFile: Parser[DeletedFile] = "deleted file mode " ~> mode <~ newline ^^ DeletedFile
    { case sl1 ~ ln1 ~ sl2 ~ ln2 => RangeInfo(sl1, ln1 getOrElse 0, sl2, ln2 getOrElse 0) }
  def apply(diff: String): List[FileDiff] = parseAll(allDiffs, diff) match {