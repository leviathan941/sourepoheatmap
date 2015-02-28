import org.sourepoheatmap.vault.git.GitDiffParser
/** Placeholder to test other parts.
  *
  * @author Alexey Kuzin <amkuzink@gmail.com>
  */
    val Some(vaultAdapter) = VaultInfoAdapter("/home/leviathan/projects/melange")

    println(vaultAdapter.getCurrentBranchName)
    println()
    vaultAdapter.getBranches.foreach(println)
    println()
    vaultAdapter.getCommitIdsBetween(1390471304, 1424523705).foreach(println)

    println()
    vaultAdapter.getCommitIdAfter(1390471304) match {
      case Some(s) => println("Commit id after: " + s)
      case None => println("No such commit")
    }
    println()

    val lastCommit = vaultAdapter.getCommitIdUntil(1424523705)
    lastCommit match {
      case Some(s) => println("Commit id until: " + s)
      case None => println("No such commit")
    }
    println("\n")

    val diff = vaultAdapter.getDiff("4bd442c137fc0", "0ba64d027223")
    diff.foreach(println)

    println("\nPARSED DIFF:")
    val gitDiffList = GitDiffParser(diff.mkString)
    for (diff <- gitDiffList) printGitDiff(diff)
  }

  private def printGitDiff(gitDiff: GitDiffParser.GitDiff): Unit = {
    println("Old file name: " + gitDiff.oldFile)
    println("New file name: " + gitDiff.newFile)
    gitDiff.fileChange match {
      case file: GitDiffParser.NewFile => println("new file mode " + file.mode)
      case file: GitDiffParser.DeletedFile => println("deleted file mode " + file.mode)
      case file: GitDiffParser.RenamedFile => println("rename from %s\nrename to %s".
        format(file.fromPath, file.toPath))
      case file: GitDiffParser.CopiedFile => println("copy from %s\ncopy to %s".
        format(file.fromPath, file.toPath))
      case _ => println("modified file")
    }
    for (chunk <- gitDiff.chunks) chunk match {
      case text: GitDiffParser.TextChunk => {
        println(text.rangeInformation)
        for (line <- text.changeLines) line match {
          case l: GitDiffParser.AddedLine => println("+" + l.line)
          case l: GitDiffParser.DeletedLine => println("-" + l.line)
          case l: GitDiffParser.ContextLine => println(" " + l.line)
          case l: GitDiffParser.WarningLine => println("\\ " + l.line)
        }
      }
      case GitDiffParser.BinaryChunk => println("Binary files differ")