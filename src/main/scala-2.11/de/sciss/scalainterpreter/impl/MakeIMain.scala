/*
 *  MakeIMain.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.scalainterpreter
package impl

import java.io.File

import de.sciss.scalainterpreter.impl.InterpreterImpl.ResultIntp

import scala.tools.nsc.interpreter.{IMain, Results}
import scala.tools.nsc.{ConsoleWriter, NewLinePrintWriter, Settings}
import scala.util.control.NonFatal

object MakeIMain {
  def apply(config: Interpreter.Config): IMain with ResultIntp = {
    val cSet = new Settings()
    cSet.classpath.value += File.pathSeparator + sys.props("java.class.path")
    val writer    = new NewLinePrintWriter(config.out.getOrElse(new ConsoleWriter), true)

    new IMain(cSet, writer) with IMainMixIn {
      // work-around for SI-8521 (Scala 2.11.0)
      override def interpret(line: String, synthetic: Boolean): Results.Result = {
        val th = Thread.currentThread()
        val cl = th.getContextClassLoader
        try {
          super.interpret(line, synthetic)
        } finally {
          th.setContextClassLoader(cl)
        }
      }
    }
  }
}