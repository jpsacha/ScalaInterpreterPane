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

import scala.collection.mutable.ListBuffer
import scala.collection.{JavaConverters, breakOut}
import scala.tools.nsc.interpreter.Completion.{ScalaCompleter, Candidates}
import scala.tools.nsc.interpreter.{IMain, Parsed, CompletionAware, JLineCompletion}

/** Abstract base class for auto-completion. This is needed because
  * JLine is differently accessed in Scala 2.10 and 2.11. There are
  * version specific final classes for these two Scala versions.
  */
abstract class AbstractScalaCompleter(intp: IMain) extends Completer {
  protected def perform(buf: String, cursor: Int, jList: java.util.List[CharSequence]): Int

  private[this] final val jLineCompletion = new JLineCompletion(intp) {
    private var importMap = Map.empty[String, Option[CompletionAware]]  // XXX TODO: should we use weak hash map?

    override def topLevel: List[CompletionAware] = {
      // println("--topLevel--")
      val sup   = super.topLevel

      val ihs = intp.importHandlers
      val res = new ListBuffer[CompletionAware]
      res ++= sup

      // try {
      ihs.foreach { ih =>
        val key = ih.expr.toString()
        importMap.get(key) match {
          case Some(Some(c)) => res += c
          case None =>
            val value = if (ih.importsWildcard) {
              import global.{NoSymbol, rootMirror}
              // rm.findMemberFromRoot()
              val sym = rootMirror.getModuleIfDefined(ih.expr.toString()) // (ih.expr.symbol.name)
              // val sym = rootMirror.getPackageObjectIfDefined(ih.expr.toString) // (ih.expr.symbol.name)
              // val pkg = rm.getPackage(global.newTermNameCached(ih.expr.toString))
              if (sym == NoSymbol) None else {
                val pc = new PackageCompletion(sym.tpe)
                res += pc
                Some(pc)
              }
            } else None
            importMap += key -> value

          case _ =>
        }
      }
      res.toList
    }

    // the first tier of top level objects (doesn't include file completion)
    override def topLevelFor(parsed: Parsed): List[String] = {
      val buf = new ListBuffer[String]
      val tl  = topLevel
      tl.foreach { ca =>
        val cac = ca.completionsFor(parsed)
        buf ++= cac

        if (buf.size > topLevelThreshold)
          return buf.toList.sorted
      }
      buf.toList
    }
  }

  private[this] final val tabCompletion = jLineCompletion.completer()

  final protected def complete1(buf: String, cursor: Int, jList: java.util.List[CharSequence]) = {
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
