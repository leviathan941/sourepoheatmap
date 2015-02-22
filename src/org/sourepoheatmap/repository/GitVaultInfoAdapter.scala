/*
 * Copyright (c) 2015, Alexey Kuzin <amkuzink@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sourepoheatmap.repository

import java.io.{ByteArrayOutputStream, IOException, File}

import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.{ListBranchCommand, Git}
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.{ObjectReader, AbbreviatedObjectId, Repository}
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.{AbstractTreeIterator, EmptyTreeIterator, CanonicalTreeParser}
import org.sourepoheatmap.repository.GitVaultInfoAdapter.GitVaultException

import scala.collection.JavaConversions._

/**
 * Class for providing ability to get information from a repository.
 *
 * @author Alexey Kuzin <amkuzink@gmail.com>
 */
class GitVaultInfoAdapter(repoPath: String) {
  require(!repoPath.isEmpty)

  private val mRepo: Repository =
    try {
      new FileRepositoryBuilder().readEnvironment().findGitDir(new File(repoPath))
        .build()
    } catch {
      case ex: java.lang.Exception => throw new GitVaultException(
        String.format("Failed to find Git repository %s.\n%s", repoPath, ex.getMessage))
    }

  def terminate(): Unit = {
    mRepo.close()
  }

  def getHeadBranchFullName(): String =
    getHeadBranch(_.getFullBranch)

  def getHeadBranchName(): String =
    getHeadBranch(_.getBranch)

  private def getHeadBranch(getBranchName: Repository => String): String = {
    mRepo.incrementOpen()
    try
      getBranchName(mRepo)
    catch {
      case ex: IOException => throw new GitVaultException("Failed to get HEAD branch name: " + ex.getMessage)
    } finally mRepo.close()
  }

  def getLocalBranches(): List[String] =
    getBranches()

  def getRemoteBranches(): List[String] =
    getBranches(_.setListMode(ListBranchCommand.ListMode.REMOTE))

  def getAllBranches(): List[String] =
    getBranches(_.setListMode(ListBranchCommand.ListMode.ALL))

  private def getBranches(getBranchList: ListBranchCommand => ListBranchCommand = (cmd => cmd)): List[String] = {
    mRepo.incrementOpen()
    try {
      val git = new Git(mRepo)
      val branchList = getBranchList(git.branchList).call.toList
      for (branch <- branchList) yield branch.getName
    } catch {
      case ex: GitAPIException => throw new GitVaultException("Failed to get branches: " + ex.getMessage)
    } finally mRepo.close()
  }

  def getCommitIdAfter(since: Int): String = {
    getCommitId(_.getCommitTime >= since, _(0))
  }

  def getCommitIdUntil(until: Int): String = {
    getCommitId(_.getCommitTime <= until, _.reverse(0))
  }

  private def getCommitId(commitCondition: RevCommit => Boolean,
                          selectElement: List[String] => String): String = {
    val commits = getCommitIds(commitCondition)
    if (commits.size == 0) "" else selectElement(commits)
  }

  def getCommitIdsBetween(since: Int, until: Int): List[String] = {
    getCommitIds(commit => (commit.getCommitTime >= since && commit.getCommitTime <= until))
  }

  private def getCommitIds(commitCondition: RevCommit => Boolean): List[String] = {
    mRepo.incrementOpen()
    try {
      val git = new Git(mRepo)
      val commitsBetween = git.log.call.toList
        .filter(commitCondition(_))
        .sortWith(_.getCommitTime < _.getCommitTime)
      for (commit <- commitsBetween) yield
        commit.getName
    } catch {
      case ex: java.lang.Exception => throw new GitVaultException("Failed to get commit IDs: " + ex.getMessage)
    } finally mRepo.close()
  }

  def getDiff(commitId: String): List[String] =
    getDiffFormatted(commitId, _ => ())

  def getDiff(oldCommitId: String, newCommitId: String): List[String] = {
    requireValidCommitHexes(oldCommitId, newCommitId)

    getDiffFormatted(
      getTreeIterator(oldCommitId, getCanonicalTreeIterator),
      getTreeIterator(newCommitId, getCanonicalTreeIterator),
      _ => ()
    )
  }

  private def getDiffFormatted(commitId: String, formatConfig: DiffFormatter => Unit): List[String] = {
    requireValidCommitHexes(commitId)

    getDiffFormatted(
      getTreeIterator(commitId, getParentTreeInterator),
      getTreeIterator(commitId, getCanonicalTreeIterator),
      formatConfig
    )
  }

  private def getDiffFormatted(
    oldTreeIter: RevWalk => AbstractTreeIterator,
    newTreeIter: RevWalk => AbstractTreeIterator,
    formatConfig: DiffFormatter => Unit
  ): List[String] = {

    mRepo.incrementOpen()
    val revWalk = new RevWalk(mRepo)
    val outStream = new ByteArrayOutputStream
    val diffFormatter = new DiffFormatter(outStream)
    try {
      diffFormatter.setRepository(mRepo)
      formatConfig(diffFormatter)

      val diffEntries = diffFormatter.scan(
        oldTreeIter(revWalk),
        newTreeIter(revWalk)
      ).toList

      for (entry <- diffEntries) yield {
        outStream.reset()
        diffFormatter.format(entry)
        outStream.toString
      }
    } catch {
      case ex: java.lang.Exception => throw new GitVaultException("Failed to get changes: " + ex.getMessage)
    } finally {
      diffFormatter.close()
      revWalk.close()
      mRepo.close()
    }
  }

//  def getInsertionLines(commitId: String): Unit = {
//    requireValidCommitHex(commitId)
//
//    mRepo.incrementOpen()
//    val revWalk = new RevWalk(mRepo)
//    try {
//      val newCommitId = revWalk.parseCommit(mRepo.resolve(commitId))
//
//      val outStream = new ByteArrayOutputStream
//      val diffFormatter = new DiffFormatter(outStream)
//      diffFormatter.setRepository(mRepo)
//      diffFormatter.setDetectRenames(false)
//      diffFormatter.setContext(0)
//      val diffEntries = diffFormatter.scan(
//        getParentTreeInterator(newCommitId, revWalk),
//        getTreeIterator(newCommitId, revWalk))
//
//      for (entry <- diffEntries) {
//        outStream.reset()
//        diffFormatter.format(entry)
//        println(outStream.toString)
//      }
//
//      diffFormatter.close()
//    } catch {
//      case ex: java.lang.Exception => throw new GitVaultException("Failed to get changes: " + ex.getMessage)
//    } finally {
//      mRepo.close()
//      revWalk.close()
//    }
//  }

  private def getTreeIterator(commitId: String, treeIter: (RevCommit, ObjectReader) => AbstractTreeIterator)
      (revWalk: RevWalk): AbstractTreeIterator =
    treeIter(revWalk.parseCommit(mRepo.resolve(commitId)), revWalk.getObjectReader)

  private def getParentTreeInterator(commit: RevCommit, objReader: ObjectReader): AbstractTreeIterator = {
    if (commit.getParentCount > 0)
      getCanonicalTreeIterator(commit.getParent(0), objReader)
    else
      new EmptyTreeIterator
  }

  private def getCanonicalTreeIterator(commit: RevCommit, objReader: ObjectReader): AbstractTreeIterator =
    new CanonicalTreeParser(null, objReader, commit.getTree)

  private def requireValidCommitHexes(hexes: String*): Unit = {
    for (hex <- hexes)
      require(AbbreviatedObjectId.isId(hex), "Argument should be valid commit hex")
  }
}

object GitVaultInfoAdapter {
  class GitVaultException(msg: String) extends Exception(msg)
}