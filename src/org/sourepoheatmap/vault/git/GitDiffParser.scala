  * =Formal grammar=
  * allDiffs        ::= { fileDiff }
  * fileDiff        ::= gitHeader extendedHeader [ unifiedHeader diffChunks ]
  * gitHeader       ::= "diff --git a/" filename [ " b/" filename ] newline
  * extendedHeader  ::= [ modeChanged ] [ similarity ] [ copiedFile | renamedFile
  *                     | deletedFile | newFile ] index
  * unifiedHeader   ::= "--- " [ "a/" ] filename newline
  *                     "+++ " [ "b/" ] filename newline
  * diffChunks      ::= { changeChunk }
  * filename        ::= """[&#94;*&%\s]+"""
  * newline         ::= """\r?\n"""
  * modeChanged     ::= "old mode " mode newline
  *                     "new mode " mode newline
  * similarity      ::= ( "similarity" | "dissimilarity" ) "index " number "%" newline
  * copiedFile      ::= "copy from " filename newline
  *                     "copy to " filename newline
  * renamedFile     ::= "rename from " filename newline
  *                     "rename to " filename newline
  * deletedFile     ::= "deleted file mode " mode newline
  * newFile         ::= "new file mode " mode newline
  * index           ::= "index " hash ".." hash [ " " mode ] newline
  * changeChunk     ::= binaryChange | ( chunkHeader { lineChange } )
  * mode            ::= """\d{6}"""
  * number          ::= """\d+"""
  * hash            ::= """[0-9a-f]{7,}"""
  * binaryChange    ::= "Binary files " [ "a/" ] filename [ " b/" ] filename
  *                     " differ" newline
  * chunkHeader     ::= rangeInfo [ contextLine ] [ newline ]
  * lineChange      ::= warningLine | contextLine | addedLine | deletedLine
  * rangeInfo       ::= "@@ -" number [ "," number ] " +" number [ "," number ] " @@"
  * warningLine     ::= "\ " commonLine newline
  * contextLine     ::= " " commonLine newline
  * addedLine       ::= "+" commonLine newline
  * deletedLine     ::= "-" commonLine newline
  * commonLine      ::= """.*"""
  *
  def allDiffs: Parser[List[FileDiff]] = rep1(fileDiff)
  def fileDiff: Parser[FileDiff] = gitHeader ~ extendedHeader ~ opt(unifiedHeader ~> diffChunks) ^^
  def gitHeader: Parser[(String, String)] =
  def unifiedHeader: Parser[(String, String)] =
  def modeChanged: Parser[(Int, Int)] = "old mode " ~> mode ~ (newline ~ "new mode " ~> mode) <~ newline ^^
  def warningLine: Parser[LineChange] = "\\ " ~> commonLine <~ newline ^^ WarningLine