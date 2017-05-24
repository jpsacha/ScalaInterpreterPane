/*
 *  AbstractScalaCompleter.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.scalainterpreter
package impl

import scala.collection.{JavaConverters, breakOut}
import scala.tools.nsc.interpreter.Completion.Candidates
import scala.tools.nsc.interpreter.{IMain, PresentationCompilerCompleter}

/** Abstract base class for auto-completion. This is needed because
  * JLine is differently accessed in Scala 2.10 and 2.11. There are
  * version specific final classes for these two Scala versions.
  */
abstract class AbstractScalaCompleter(intp: IMain) extends Completer {
  protected def perform(buf: String, cursor: Int, jList: java.util.List[CharSequence]): Int

  private[this] final val tabCompletion = new PresentationCompilerCompleter(intp)

  def resetVerbosity(): Unit = ()

  final protected def complete1(buf: String, cursor: Int, jList: java.util.List[CharSequence]): Int = {
    val buf1 = if (buf == null) "" else buf
    val Candidates(newCursor, newCandidates) = tabCompletion.complete(buf1, cursor)
    newCandidates.foreach(jList.add)
    newCursor
  }

  final def complete(buf: String, cursor: Int): Candidates = {
    val jList     = new java.util.ArrayList[CharSequence]
    val newCursor = perform(buf, cursor, jList) // argComp.complete(buf, cursor, jList)
    import JavaConverters._
    val list: List[String] = jList.asScala.collect {
      case c if c.length > 0 => c.toString
    } (breakOut)
    Candidates(newCursor, list)
  }
}