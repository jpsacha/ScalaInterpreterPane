/*
 *  InterpreterImpl.scala
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

import java.io.{File, Writer}

import scala.collection.immutable.{Seq => ISeq}
import scala.tools.nsc.interpreter.{IMain, Results}
import scala.tools.nsc.{ConsoleWriter, NewLinePrintWriter, Settings => CompilerSettings}
import scala.util.control.NonFatal

object InterpreterImpl {
  import Interpreter.{Config, ConfigBuilder, Error, Incomplete, Result, Success}

  def apply(config: Config): Interpreter = {
    val in = makeIMain(config)
    new Impl(in)
  }

  def newConfigBuilder(): ConfigBuilder = new ConfigBuilderImpl

  def mkConfigBuilder(config: Config): ConfigBuilder = {
    import config._
    val b           = new ConfigBuilderImpl
    b.imports       = imports
    b.bindings      = bindings
    b.executor      = executor
    b.out           = out
    b.quietImports  = quietImports
    b
  }

  private final class ConfigBuilderImpl extends ConfigBuilder {
    var imports     : ISeq[String]      = Nil
    var bindings    : ISeq[NamedParam]  = Nil
    var executor      = ""
    var out           = Option.empty[Writer]
    var quietImports  = true

    def build: Config = ConfigImpl(
      imports = imports, bindings = bindings, executor = executor, out = out, quietImports = quietImports)

    override def toString = s"Interpreter.ConfigBuilder@${hashCode().toHexString}"
  }

  private final case class ConfigImpl(imports: ISeq[String], bindings: ISeq[NamedParam],
                                      executor: String, out: Option[Writer], quietImports: Boolean)
    extends Config {

    override def toString = s"Interpreter.Config@${hashCode().toHexString}"
  }

  private trait ResultIntp {
    def interpretWithResult(   line: String, synthetic: Boolean = false): Result
    def interpretWithoutResult(line: String, synthetic: Boolean = false): Result
  }

  private def makeIMain(config: Config): IMain with ResultIntp = {
    val cSet = new CompilerSettings()
    cSet.classpath.value += File.pathSeparator + sys.props("java.class.path")
    val in: IMain with ResultIntp =
      new IMain(cSet, new NewLinePrintWriter(config.out getOrElse new ConsoleWriter, true)) with ResultIntp {
        override protected def parentClassLoader: ClassLoader = Interpreter.getClass.getClassLoader

        // note `lastRequest` was added in 2.10
        private def _lastRequest = prevRequestList.last

        // private in Scala API
        private def mostRecentlyHandledTree: Option[global.Tree] = {
          import memberHandlers._
          import naming._
          prevRequestList.reverse.foreach { req =>
            req.handlers.reverse foreach {
              case x: MemberDefHandler if x.definesValue && !isInternalTermName(x.name) => return Some(x.member)
              case _ => ()
            }
          }
          None
        }

        def interpretWithResult(line: String, synthetic: Boolean): Result = {
          val res0 = interpretWithoutResult(line, synthetic)
          res0 match {
            case Success(name, _) => try {
              import global._
              val shouldEval = mostRecentlyHandledTree.exists {
                case _: ValDef            => true
                case Assign(Ident(_), _)  => true
                case ModuleDef(_, _, _)   => true
                case _                    => false
              }
              // println(s"shouldEval = $shouldEval")
              Success(name, if (shouldEval) _lastRequest.lineRep.call("$result") else ())
            } catch {
              case NonFatal(_) => res0
            }
            case _ => res0
          }
        }

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

        def interpretWithoutResult(line: String, synthetic: Boolean): Result = {
          interpret(line, synthetic) match {
            case Results.Success    => Success(mostRecentVar, ())
            case Results.Error      => Error("Error") // doesn't work anymore with 2.10.0-M7: _lastRequest.lineRep.evalCaught.map( _.toString ).getOrElse( "Error" ))
            case Results.Incomplete => Incomplete
          }
        }
      }

    // this was removed in Scala 2.11
    def quietImport(ids: Seq[String]): Results.Result = in.beQuietDuring(addImports(ids))

    // this was removed in Scala 2.11
    def addImports(ids: Seq[String]): Results.Result =
      if (ids.isEmpty) Results.Success
      else in.interpret(ids.mkString("import ", ", ", ""))

    in.setContextClassLoader()    // needed in Scala 2.11.
    config.bindings.foreach(in.bind)
    if (config.quietImports) quietImport(config.imports) else addImports(config.imports)
    in.setExecutionWrapper(config.executor)
    in
  }

  private final class Impl(in: IMain with ResultIntp) extends Interpreter {
    private lazy val cmp: Completer = new ScalaCompleterImpl(in)

    override def toString = s"Interpreter@${hashCode().toHexString}"

    def completer: Completer = cmp

    def interpretWithResult(code: String, quiet: Boolean): Interpreter.Result =
      if (quiet) {
        in.beQuietDuring(in.interpretWithResult(code))
      } else {
        in.interpretWithResult(code)
      }

    def interpret(code: String, quiet: Boolean): Interpreter.Result =
      if (quiet) {
        in.beQuietDuring(in.interpretWithoutResult(code))
      } else {
        in.interpretWithoutResult(code)
      }
  }
}