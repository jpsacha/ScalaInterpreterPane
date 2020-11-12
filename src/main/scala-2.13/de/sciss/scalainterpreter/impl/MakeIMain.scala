/*
 *  MakeIMain.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2020 Hanns Holger Rutz. All rights reserved.
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

import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.shell.ReplReporterImpl
import scala.tools.nsc.{ConsoleWriter, NewLinePrintWriter, Settings}

object MakeIMain {
  def apply(config: Interpreter.Config): IMain with ResultIntp = {
    // otherwise Scala 2.12 REPL results will print strange control chars
    System.setProperty("scala.color", "false")
    val cSet = new Settings()
    cSet.classpath.value += File.pathSeparator + sys.props("java.class.path")
    val writer    = new NewLinePrintWriter(config.out.getOrElse(new ConsoleWriter), true)
    val reporter  = new ReplReporterImpl(cSet, writer)

    new IMain(cSet, reporter) with IMainMixIn
//    {
//      // work-around for SI-8521 (Scala 2.13.0-M5)
//      override def interpret(line: String, synthetic: Boolean): Results.Result = {
//        val th = Thread.currentThread()
//        val cl = th.getContextClassLoader
//        try {
//          super.interpret(line, synthetic)
//        } finally {
//          th.setContextClassLoader(cl)
//        }
//      }
//    }
  }
}