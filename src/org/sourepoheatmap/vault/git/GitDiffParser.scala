  * fileDiff        ::= gitHeader extendedHeader [ unifiedHeader ] [ diffChunks ]
  def fileDiff: Parser[FileDiff] = gitHeader ~ extendedHeader ~ (opt(unifiedHeader) ~> opt(diffChunks)) ^^
    { case files ~ change ~ chunks => FileDiff(files._1, files._2, change, chunks getOrElse Nil) }