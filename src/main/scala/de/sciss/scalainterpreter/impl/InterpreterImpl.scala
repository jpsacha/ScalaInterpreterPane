/*
 *  InterpreterImpl.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.scalainterpreter
package impl

import java.io.{Writer, File}

// this is a trick that makes it work both in Scala 2.10 and 2.11 due to 'jline' sub-package
import scala.tools._

import scala.tools.nsc.interpreter._
import Completion.{Candidates, ScalaCompleter}
import jline.console.completer.{Completer, ArgumentCompleter}

import scala.collection.{JavaConverters, breakOut}
import scala.collection.immutable.{Seq => ISeq}
import scala.collection.mutable.ListBuffer
import scala.tools.nsc.{ConsoleWriter, NewLinePrintWriter, Settings => CompilerSettings}
import scala.tools.nsc.interpreter.Completion.{Candidates, ScalaCompleter}

import scala.util.control.NonFatal

object InterpreterImpl {
  import Interpreter.{Config, ConfigBuilder, Result, Success, Error, Incomplete}

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
    var imports       = ISeq.empty[String]
    var bindings      = ISeq.empty[NamedParam]
    var executor      = ""
    var out           = Option.empty[Writer]
    var quietImports  = true

    def build: Config = new ConfigImpl(
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
    val in = new IMain(cSet, new NewLinePrintWriter(config.out getOrElse new ConsoleWriter, true)) with ResultIntp {
      override protected def parentClassLoader = Interpreter.getClass.getClassLoader

      // note `lastRequest` was added in 2.10
      private def _lastRequest = prevRequestList.last

      // private in Scala API
      private def mostRecentlyHandledTree: Option[global.Tree] = {
        import naming._
        import memberHandlers._
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
              case x: ValDef            => true
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
      override def interpret(line: String, synthetic: Boolean): IR.Result = {
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
    def quietImport(ids: Seq[String]): IR.Result = in.beQuietDuring(addImports(ids))

    // this was removed in Scala 2.11
    def addImports(ids: Seq[String]): IR.Result =
      if (ids.isEmpty) IR.Success
      else in.interpret(ids.mkString("import ", ", ", ""))

    in.setContextClassLoader()
    config.bindings.foreach(in.bind)
    if (config.quietImports) quietImport(config.imports) else addImports(config.imports)
    in.setExecutionWrapper(config.executor)
    in
  }

  private final class Impl(in: IMain with ResultIntp) extends Interpreter {
    // private var importMap = Map.empty[in.memberHandlers.ImportHandler, Option[CompletionAware]]

    private var importMap = Map.empty[String, Option[CompletionAware]]  // XXX TODO: should we use weak hash map?

    private lazy val cmp: ScalaCompleter = {
      val jLineComp = new JLineCompletion(in) {

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
                  import global.{rootMirror, NoSymbol}
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
      val tc = jLineComp.completer()

      val comp = new Completer {
        def complete(buf: String, cursor: Int, candidates: JList[CharSequence]): Int = {
          val buf1 = if (buf == null) "" else buf
          val Candidates(newCursor, newCandidates) = tc.complete(buf1, cursor)
          newCandidates.foreach(candidates.add)
          newCursor
        }
      }

      val argComp = new ArgumentCompleter(new JLineDelimiter, comp)
      argComp.setStrict(false)

      new ScalaCompleter {
        def complete(buf: String, cursor: Int): Candidates = {
          val jList     = new java.util.ArrayList[CharSequence]
          val newCursor = argComp.complete(buf, cursor, jList)
          import JavaConverters._
          val list: List[String] = jList.asScala.collect {
            case c if c.length > 0 => c.toString
          } (breakOut)
          Candidates(newCursor, list)
        }
      }
    }

    override def toString = s"Interpreter@${hashCode().toHexString}"

    def completer: Completion.ScalaCompleter = cmp

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