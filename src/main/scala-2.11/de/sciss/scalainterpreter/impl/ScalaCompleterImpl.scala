/*
 *  ScalaCompleterImpl.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.scalainterpreter.impl

import _root_.jline.console.completer.{ArgumentCompleter, Completer}

import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.jline.JLineDelimiter

/** Completer implementation for Scala 2.11. */
final class ScalaCompleterImpl(intp: IMain) extends AbstractScalaCompleter(intp) {
  self =>

  private[this] val comp = new Completer {
    def complete(buf: String, cursor: Int, candidates: java.util.List[CharSequence]): Int =
      self.complete1(buf, cursor, candidates)
  }

  private[this] val argComp = new ArgumentCompleter(new JLineDelimiter, comp)
  argComp.setStrict(false)

  protected def perform(buf: String, cursor: Int, jList: java.util.List[CharSequence]): Int =
    argComp.complete(buf, cursor, jList)
}
