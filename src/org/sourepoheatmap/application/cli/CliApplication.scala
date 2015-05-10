    println("Vault type = " + vaultAdapter.getVaultType)
//    testRepoVaultInfoAdapter(vaultAdapter)
//    testGitVaultInfoAdapter(vaultAdapter)
  }

  private def testRepoVaultInfoAdapter(vaultAdapter: VaultInfoAdapter): Unit = {
    println("Branches:")
    vaultAdapter.getBranches.foreach(println)
  }

  private def testGitVaultInfoAdapter(vaultAdapter: VaultInfoAdapter): Unit = {
    println()
    println("Current branch before = " + vaultAdapter.getCurrentBranchName)
    vaultAdapter.switchBranch("refs/heads/master")
    println("Current branch after = " + vaultAdapter.getCurrentBranchName)

    //    GitDiffParser(diff2.mkString).foreach(printGitDiff)