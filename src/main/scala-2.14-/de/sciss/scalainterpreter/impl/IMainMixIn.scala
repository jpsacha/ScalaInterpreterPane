/*
 *  IMainMixIn.scala
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

import scala.tools.nsc.interpreter.{IMain, Results, NamedParam}
import scala.util.control.NonFatal

trait IMainMixIn extends IntpInterface {
  self: IMain =>

  override protected def parentClassLoader: ClassLoader = Interpreter.getClass.getClassLoader

  def bind(tup: (String, Any)): Unit =
    bind(NamedParam(tup._1, tup._2))

  def mkCompleter(): Completer = new ScalaCompleterImpl(self)

  // private in Scala API
  private def mostRecentlyHandledTree: Option[global.Tree] = {
    import memberHandlers._
    import naming._
    val it = prevRequestList.reverseIterator.flatMap { req =>
      req.handlers.reverse.collectFirst {
        case x: MemberDefHandler if x.definesValue && !isInternalTermName(x.name) => x.member
      }
    }
    if (it.hasNext) Some(it.next()) else None // `nextOption()` requires Scala 2.13
  }

  def interpretWithResult(line: String, synthetic: Boolean, quiet: Boolean): Interpreter.Result = {
    val res0 = interpretWithoutResult(line, synthetic = synthetic, quiet = quiet)
    res0 match {
      case Interpreter.Success(name, _) => try {
        import global._
        val shouldEval = mostRecentlyHandledTree.exists {
          case _: ValDef            => true
          case Assign(Ident(_), _)  => true
          case ModuleDef(_, _, _)   => true
          case _                    => false
        }
        // println(s"shouldEval = $shouldEval")
        Interpreter.Success(name, if (shouldEval) lastRequest.lineRep.call("$result") else ())
      } catch {
        case NonFatal(_) => res0
      }
      case _ => res0
    }
  }

  def interpretWithoutResult(line: String, synthetic: Boolean, quiet: Boolean): Interpreter.Result = {
    var res: Results.Result = null
    if (quiet) beQuietDuring { res = interpret(line, synthetic) } else { res = interpret(line, synthetic) }
    res match {
      case Results.Success    => Interpreter.Success(mostRecentVar, ())
      case Results.Error      => Interpreter.Error("Error") // doesn't work anymore with 2.10.0-M7: _lastRequest.lineRep.evalCaught.map( _.toString ).getOrElse( "Error" ))
      case Results.Incomplete => Interpreter.Incomplete
    }
  }
}
