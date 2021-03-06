/*
 * Copyright (c) 2015, 2016 Alexey Kuzin <amkuzink@gmail.com>
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

package sourepoheatmap.treemap

import scala.collection.mutable.ListBuffer

/** Class to represent generic Treemap.
  *
  * @author Alexey Kuzin <amkuzink@gmail.com>
  */
class Treemap[R <: TreemapRectangle](
    private val boxWidth: Double,
    private val boxHeight: Double,
    diffInfo: Map[String, Int],
    private val creator: TreemapRectangle.RectAttrs => R = TreemapRectangle.SimpleRectangle.tupled) {
  require(boxWidth > 0.0 && boxHeight > 0.0 && diffInfo.nonEmpty)

  private val mDiffInfo = sortByWeight(diffInfo.toList)
  private val mScale = getScale(boxWidth, boxHeight, mDiffInfo)

  def rectangles: List[R] = {
    val rectsBuffer = new ListBuffer[R]
    squarify(mDiffInfo, Nil, (0, 0), (boxWidth, boxHeight), rectsBuffer)
    rectsBuffer.toList
  }

  private def getScale(width: Double, height: Double, rects: List[(String, Int)]): Double = {
    width * height / sumWeights(rects)
  }

  private def sortByWeight(rectInfo: List[(String, Int)]): List[(String, Int)] = {
    rectInfo.sortBy(- _._2)
  }

  private def sumWeights(rects: List[(String, Int)]): Int = {
    (0 /: rects) (_ + _._2)
  }

  /** Recursive method to build squarified treemap.
    * Squarify algorithm developed by M. Bruls, K. Huizing and J.J. van Wijk is used here.
    *
    * @param nodes Nodes which are not placed in the treemap yet.
    * @param row Current treemap row which will be put soon.
    * @param pos Current position of the empty area. Next rectangle will be placed here.
    * @param dimen Current dimension of the empty area.
    * @param rectsBuffer [[R]] where treemap [[TreemapRectangle]]s will be put.
    * @see [[http://www.win.tue.nl/~vanwijk/stm.pdf Squarified Treemaps article]]
    */
  private def squarify(nodes: List[(String, Int)],
      row: List[(String, Int)],
      pos: (Double, Double),
      dimen: (Double, Double),
      rectsBuffer: ListBuffer[R]): Unit = {
    try {
      val rowWith = row ::: List(nodes.head)
      val w = getShortestSide(dimen._1, dimen._2)

      if ((row == Nil) || (worst(rowWith, w) < worst(row, w))) {
        squarify(nodes.tail, rowWith, pos, dimen, rectsBuffer)
      } else {
        val (newPos, newDimen) = layoutRow(row, pos, dimen, rectsBuffer)
        squarify(nodes, Nil, newPos, newDimen, rectsBuffer)
      }
    } catch {
      case ex: NoSuchElementException => layoutRow(row, pos, dimen, rectsBuffer)
    }

  }

  /** Method to get the worst aspect ratio in the row.
    *
    * @param row Nodes row where weights we can get.
    * @param w Shortest side of the empty area.
    * @return the worst aspect ratio.
    */
  private def worst(row: List[(String, Int)], w: Double): Double = {
    val sum = sumWeights(row) * mScale
    val maxWeight = row.maxBy(_._2)._2 * mScale
    val minWeight = row.minBy(_._2)._2 * mScale
    math.max((w * w * maxWeight) / (sum * sum), (sum * sum) / (w * w * minWeight))
  }

  private def getShortestSide(dimen: (Double, Double)): Double = {
    if (dimen._2 < dimen._1) dimen._2 else dimen._1
  }

  private def layoutRow(row: List[(String, Int)], pos: (Double, Double), dimen: (Double, Double),
      rectsBuffer: ListBuffer[R]):
      ((Double, Double), (Double, Double)) = {
    // Longest side length
    val rectLong = sumWeights(row) * mScale / getShortestSide(dimen)
    // Is layout vertical
    val vertical = dimen._2 < dimen._1

    rectsBuffer ++= createRow(row, pos, rectLong, vertical).toList

    if (vertical) {
      ((pos._1 + rectLong, pos._2), (dimen._1 - rectLong, dimen._2))
    } else {
      ((pos._1, pos._2 + rectLong), (dimen._1, dimen._2 - rectLong))
    }
  }

  private def createRow(row: List[(String, Int)],
      pos: (Double, Double),
      rectLong: Double,
      vertical: Boolean): Stream[R] = {

    if (row.isEmpty) {
      Stream.empty
    } else {
      val rectShort = row.head._2 * mScale / rectLong
      if (vertical) {
        val rectY = pos._2 + rectShort
        TreemapRectangle(pos, (rectLong, rectShort), row.head, creator) #::
            createRow(row.tail, (pos._1, rectY), rectLong, vertical)
      } else {
        val rectX = pos._1 + rectShort
        TreemapRectangle(pos, (rectShort, rectLong), row.head, creator) #::
            createRow(row.tail, (rectX, pos._2), rectLong, vertical)
      }
    }
  }
}
