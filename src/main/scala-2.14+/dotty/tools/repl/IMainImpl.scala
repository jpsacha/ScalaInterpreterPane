/*
 *  IMainImpl.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package dotty.tools.repl

import java.io.Writer
import java.io.PrintStream
import java.io.OutputStream

import de.sciss.scalainterpreter.impl.IntpInterface
import de.sciss.scalainterpreter.Completer
import de.sciss.scalainterpreter.Completion
import de.sciss.scalainterpreter.Interpreter
import dotty.tools.dotc.{interactive => dotci}

// rather quickly hacked wrapper around Dotty's ReplDriver
final class IMainImpl(out: PrintStream, loader: ClassLoader) extends IntpInterface with Completer {
  private object driver extends ReplDriver(Array("-usejavacp", "-color:never", "-Xrepl-disable-display"), out, Some(loader)) {
    def complete(buffer: String, cursor: Int): Completion.Result = {
      // val jlineCand = completions(cursor, buffer, state)
      // val ourCand = jlineCand.map { jl =>
      //   println(s"value = '${jl.value}', label = '${jl.label}', descr = '${jl.descr}', displ = '${jl.displ}', suffix = '${jl.suffix}', key = '${jl.key}', complete = '${jl.complete}'")
      //   // Completion.Def(jl.value, jl.descr, isModule = false)
      //   Completion.Simple(jl.suffix)
      // }

      // all that stuff is `private` ...
      // implicit val state0 = newRun(state)
      // compiler
      //   .typeCheck(expr, errorsAllowed = true)
      //   .map { tree =>
      //     val file = SourceFile.virtual("<completions>", expr, maybeIncomplete = true)
      //     val unit = CompilationUnit(file)(using state.context)
      //     unit.tpdTree = tree
      //     given Context = state.context.fresh.setCompilationUnit(unit)
      //     val srcPos = SourcePosition(file, Span(cursor))
      //     val (_, completions) = Completion.completions(srcPos)
      val ourCand = Nil
      Completion.Result(cursor, ourCand)
    }
  }

  private var state     = driver.initialState
  private val rendering = new Rendering(Some(getClass.getClassLoader))

  def bind(tup: (String, Any)): Unit = 
    state = driver.bind(tup._1, tup._2)(state)

  def clearExecutionWrapper(): Unit = ()

  def setExecutionWrapper(code: String): Unit = ()

  def mkCompleter(): Completer = this

  def interpretWithoutResult(line: String, synthetic: Boolean, quiet: Boolean): Interpreter.Result = {
    // println(s"interpretWithoutResult($line, $synthetic, $quiet)")
    // state = driver.run(line)(state)
    // Interpreter.Success("", ())
    interpretWithResult(line, synthetic = synthetic, quiet = quiet)
  }

  private final val REPL_SESSION_LINE  = "rs$line$"
  private final val REPL_RES_PREFIX    = "res"

  def interpretWithResult(line: String, synthetic: Boolean, quiet: Boolean): Interpreter.Result = {
    // println(s"interpretWithResult($line, $synthetic, $quiet)")
    val vid = state.valIndex
    state = driver.run(line)(state)
    // import dotc.core.StdNames.str
    val oid = state.objectIndex
    val methodOpt = Class.forName(s"${/* str. */REPL_SESSION_LINE}$oid", true, rendering.classLoader()(using state.context))
      .getDeclaredMethods.find(_.getName == s"${/* str. */REPL_RES_PREFIX}$vid")
    val valueOpt: Option[Any] = methodOpt
      .map(_.invoke(null))
    val value = valueOpt.getOrElse(())
    val methodName = methodOpt.fold("")(_.getName)
    if (!quiet && methodOpt.isDefined && valueOpt.isDefined && !valueOpt.contains(())) {
      println(s"$methodName: $value")
    }
    Interpreter.Success(methodName, value)
  }
  
  // ---- Completer ----

  def complete(buffer: String, cursor: Int, tabCount: Int): Completion.Result =
    driver.complete(buffer, cursor)
}