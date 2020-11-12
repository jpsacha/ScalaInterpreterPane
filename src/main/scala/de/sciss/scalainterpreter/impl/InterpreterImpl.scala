/*
 *  InterpreterImpl.scala
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

import java.io.Writer

import scala.collection.immutable.{Seq => ISeq}
import scala.tools.nsc.interpreter.{IMain, Results}

object InterpreterImpl {
  import Interpreter.{Config, ConfigBuilder, Result}

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

  trait ResultIntp {
    def interpretWithResult(   line: String, synthetic: Boolean = false): Result
    def interpretWithoutResult(line: String, synthetic: Boolean = false): Result
  }

  private def makeIMain(config: Config): IMain with ResultIntp = {
    val in: IMain with ResultIntp = MakeIMain(config)

    // this was removed in Scala 2.11
    def quietImport(ids: Seq[String]): Results.Result = {
      // bloody Scala 2.13 removes return type
      var res: Results.Result = null
      in.beQuietDuring {
        res = addImports(ids)
      }
      res
    }

    // this was removed in Scala 2.11
    def addImports(ids: Seq[String]): Results.Result =
      if (ids.isEmpty) Results.Success
      else in.interpret(ids.mkString("import ", ", ", ""))

    // in.setContextClassLoader()    // needed in Scala 2.11.
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
        // bloody Scala 2.13 removes return type
        var res: Result = null
        in.beQuietDuring {
          res = in.interpretWithResult(code)
        }
        res
      } else {
        in.interpretWithResult(code)
      }

    def interpret(code: String, quiet: Boolean): Interpreter.Result =
      if (quiet) {
        // bloody Scala 2.13 removes return type
        var res: Result = null
        in.beQuietDuring {
          res = in.interpretWithoutResult(code)
        }
        res
      } else {
        in.interpretWithoutResult(code)
      }
  }
}