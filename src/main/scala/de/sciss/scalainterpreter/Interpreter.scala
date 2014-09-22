/*
 *  Interpreter.scala
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

import scala.tools._  // this is a trick that makes it work both in Scala 2.10 and 2.11 due to 'jline' sub-package
import nsc.{Settings => CompilerSettings, ConsoleWriter, NewLinePrintWriter}
import scala.tools.nsc.interpreter._
import Completion.{Candidates, ScalaCompleter}
import jline.console.completer.{Completer, ArgumentCompleter}
import java.io.{Writer, File}
import scala.collection.immutable.{Seq => ISeq}
import scala.util.control.NonFatal
import scala.collection.{breakOut, JavaConverters}
import scala.collection.mutable.ListBuffer
import language.implicitConversions
import scala.concurrent.{ExecutionContext, Future, blocking}
import java.util.concurrent.Executors
import scala.tools.nsc.interpreter.Completion.Candidates

/** The `Interpreter` wraps the underlying Scala interpreter functionality. */
object Interpreter {
  val defaultInitializeContext: ExecutionContext =
    ExecutionContext fromExecutorService Executors.newSingleThreadExecutor()

  /** Factory object for creating new configuration builders. */
  object Config {
    /** A configuration builder is automatically converted to an immutable configuration */
    implicit def build(b: ConfigBuilder): Config = b.build

    /** Creates a new configuration builder with defaults. */
    def apply(): ConfigBuilder = new ConfigBuilderImpl
  }
  sealed trait ConfigLike {
    implicit def build(b: ConfigBuilder): Config = b.build

    /** A list of package names to import to the scope of the interpreter. */
    def imports: ISeq[String]

    /** A list of bindings which make objects in the hosting environment available to the interpreter under a
      * given name.
      */
    def bindings: ISeq[NamedParam]

    /** An injected code fragment which precedes the evaluation of the each interpreted line's wrapping object.
      *
      * For example if the interpreted code was `val foo = 33`, the actually compiled code looks like
      *
      * {{{
      *   val res = <execution> { object <synthetic> { val foo = 33 }}
      * }}}
      *
      * The executor can be used for example to set a particular context needed during the evaluation of the object's
      * body. Then most probably it will be defined to take a thunk argument, for instance:
      *
      * {{{
      *   object MyExecutor { def apply[A](thunk: => A): A = concurrent.stm.atomic(_ => thunk)
      *   config.executor = "MyExecutor"
      * }}}
      *
      * Then the evaluated code may find the STM transaction using `Txn.findCurrent`
      */
    def executor: String

    /** The interpreter's output printing device. */
    def out:          Option[Writer]

    /** Whether initial imports should be performed silently (`true`) or not (`false`). Not silent means the imported
      * packages' names will be printed to the default printing device (`out`).
      */
    def quietImports: Boolean
  }

  /** Configuration for an interpreter. */
  sealed trait Config extends ConfigLike

  object ConfigBuilder {
    /** Creates a new configuration builder initialized to the values taken from an existing configuration.
      *
      * @param config  the configuration from which to take the initial settings
      * @return        the new mutable configuration builder
      */
    def apply(config: Config): ConfigBuilder = {
      import config._
      val b           = new ConfigBuilderImpl
      b.imports       = imports
      b.bindings      = bindings
      b.executor      = executor
      b.out           = out
      b.quietImports  = quietImports
      b
    }
  }

  sealed trait ConfigBuilder extends ConfigLike {
    var imports     : ISeq[String]
    var bindings    : ISeq[NamedParam]
    var executor    : String
    var out         : Option[Writer]
    var quietImports: Boolean

    def build: Config
  }

  sealed trait Result
  case class Success(resultName: String, resultValue: Any) extends Result
  case class Error(message: String) extends Result // can't find a way to get the exception right now
  case object Incomplete extends Result

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

  /** Creates a new interpreter with the given settings.
    *
    * @param config  the configuration for the interpreter.
    * @return  the new Scala interpreter
    */
  def apply(config: Config = Config().build): Interpreter = {
    val in = makeIMain(config)
    new Impl(in)
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
            //            val mrv = mostRecentlyHandledTree.flatMap {
            //              case x: ValDef => Some(x.name)
            //              case Assign(Ident(n), _)   => Some(n)
            //              case ModuleDef(_, n, _)    => Some(n)
            //              case _                        =>
            //                val n = naming.mostRecentVar
            //                if (n.isEmpty) None else Some(n)
            //            }
            val shouldEval = mostRecentlyHandledTree.exists {
              case x: ValDef =>
                // println("ValDef")
                true
              case Assign(Ident(_), _) =>
                // println("Assign")
                true
              case ModuleDef(_, _, _)  =>
                // println("ModuleDef")
                true
              case _ =>
                // println("naming")
                // val n = naming.mostRecentVar
                // !n.isEmpty
                false
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

  /** Convenience constructor with calls `apply` inside a blocking future. */
  def async(config: Config = Config().build)
           (implicit exec: ExecutionContext = defaultInitializeContext): Future[Interpreter] = Future {
    blocking(apply(config))
  }

  private final class Impl(in: IMain with ResultIntp) extends Interpreter {
    // private var importMap = Map.empty[in.memberHandlers.ImportHandler, Option[CompletionAware]]

    private var importMap = Map.empty[String, Option[CompletionAware]]  // XXX TODO: should we use weak hash map?

    private lazy val cmp: ScalaCompleter = {
      val jlineComp = new JLineCompletion(in) {
        //        private def imported: List[ImportCompletion] = {
        //          val wc  = intp.sessionWildcards
        //          intp.sessionImportedSymbols
        //          val res = wc.map(TypeMemberCompletion.imported)
        //          res
        //        }

        override def topLevel: List[CompletionAware] = {
          // println("--topLevel--")
          val sup   = super.topLevel

          val ihs = intp.importHandlers
          //          ihs.foreach {
          //            case ih if ih.importsWildcard =>
          //              println("---imported symbols---")
          //              ih.importedSymbols.foreach(println)
          //              println(s"isLegalTopLevel? ${ih.isLegalTopLevel} isPredefImport? ${ih.isPredefImport} importsWildcard? ${ih.importsWildcard}")
          //              println(s"importString: '${ih.importString}' ; expr '${ih.expr}'")
          //              println(s"targetType: ${ih.targetType}")
          //              println("---imported names---")
          //              ih.importedNames.foreach(println)
          //              println("---selectors---")
          //              ih.selectors.foreach(println)
          //              println("---wildcard names---")
          //              ih.wildcardNames.foreach(println)
          //            case _ =>
          //          }

          //          println("---all seen types---")
          //          intp.allSeenTypes.foreach(println)
          //          println("---definedTerms---")
          //          intp.definedTerms.foreach(println)
          //          println("---definedTypes---")
          //          intp.definedTypes.foreach(println)
          //          println("---visibleTermNames---")
          //          intp.visibleTermNames.foreach(println)
          //          println("---allDefinedNames---")
          //          intp.allDefinedNames.foreach(println)

          //          val add: List[CompletionAware] = intp.importHandlers.flatMap {
          //            case ih if ih.importsWildcard => ih.
          //          }
          // val testPck = rm.getPackage(global.newTermNameCached("scala.concurrent"))
          // val testCmp = new PackageCompletion(testPck.tpe)

          // val res = topLevelBase ++ imported
          // res.foreach(println)
          // val add: List[CompletionAware] = testCmp :: Nil // CompletionAware(() => intp.importedTypes.map(_.decode)) :: Nil

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
          //} catch {
          //  case NonFatal(ex) => ex.printStackTrace()
          //}

          //          val add: List[CompletionAware] = ihs.flatMap { ih =>
          //            if (!ih.importsWildcard) None else {
          //              // println(ih.expr.getClass)
          //              import global.{rootMirror, NoSymbol}
          //              // rm.findMemberFromRoot()
          //              val sym = rootMirror.getModuleIfDefined(ih.expr.toString) // (ih.expr.symbol.name)
          //              // val sym = rootMirror.getPackageObjectIfDefined(ih.expr.toString) // (ih.expr.symbol.name)
          //              // val pkg = rm.getPackage(global.newTermNameCached(ih.expr.toString))
          //              if (sym == NoSymbol) None else {
          //                val pc = new PackageCompletion(sym.tpe)
          //                Some(pc)
          //              }
          //            }
          //          }
          //          try {
          //          } catch {
          //            case NonFatal(ex) => ex.printStackTrace()
          //              throw ex
          //          }
          // val res = sup ++ add
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
      val tc = jlineComp.completer()

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
//
      new ScalaCompleter {
        def complete(buf: String, cursor: Int): Candidates = {
          val jlist     = new java.util.ArrayList[CharSequence]
          val newCursor = argComp.complete(buf, cursor, jlist)
          import JavaConverters._
          val list: List[String] = jlist.asScala.collect {
            case c if c.length > 0 => c.toString
          } (breakOut)
          Candidates(newCursor, list)
        }
      }
    }

    override def toString = "Interpreter@" + hashCode().toHexString

    def completer: Completion.ScalaCompleter = cmp

    def interpret(code: String, quiet: Boolean): Interpreter.Result = {
      if (quiet) {
        in.beQuietDuring(in.interpretWithResult(code))
      } else {
        in.interpretWithResult(code)
      }
    }

    def interpretWithoutResult(code: String, quiet: Boolean): Interpreter.Result = {
      if (quiet) {
        in.beQuietDuring(in.interpretWithoutResult(code))
      } else {
        in.interpretWithoutResult(code)
      }
    }
  }
}
/** The `Interpreter` wraps the underlying Scala interpreter functionality. */
trait Interpreter {
  /** Interprets a piece of code
    *
    * @param code    the source code to interpret
    * @param quiet   whether to suppress result printing (`true`) or not (`false`)
    *
    * @return        the result of the execution of the interpreted code
    */
  def interpret(code: String, quiet: Boolean = false): Interpreter.Result

  /** Interprets a piece of code. Unlike `interpret` the result is not evaluated. That is, in the case
    * off `Success` the result value will always be `()`.
    */
  def interpretWithoutResult(code: String, quiet: Boolean = false): Interpreter.Result

  /** A code completion component which may be attached to an editor. */
  def completer: Completion.ScalaCompleter
}
