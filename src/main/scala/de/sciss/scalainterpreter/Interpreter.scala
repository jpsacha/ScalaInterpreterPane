/*
 *  Interpreter.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.scalainterpreter

import tools.nsc.{Settings => CompilerSettings, ConsoleWriter, NewLinePrintWriter}
import java.io.{Writer, File}
import scala.tools.nsc.interpreter._
import java.util.concurrent.Executors
import collection.immutable.{Seq => ISeq}
import scala.util.control.NonFatal
import scala.tools.nsc.interpreter.Completion.{Candidates, ScalaCompleter}
import scala.tools.jline.console.completer.{Completer, ArgumentCompleter}
import scala.collection.{breakOut, JavaConverters}
import scala.collection.mutable.ListBuffer

/** The `Interpreter` wraps the underlying Scala interpreter functionality. */
object Interpreter {
  /** Factory object for creating new configuration builders. */
  object Config {
    /** A configuration builder is automatically converted to an immutable configuration */
    implicit def build(b: ConfigBuilder): Config = b.build()

    /** Creates a new configuration builder with defaults. */
    def apply(): ConfigBuilder = new ConfigBuilderImpl
  }
  sealed trait ConfigLike {
    implicit def build(b: ConfigBuilder): Config = b.build()

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

    def build(): Config
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

    def build(): Config = new ConfigImpl(
      imports = imports, bindings = bindings, executor = executor, out = out, quietImports = quietImports)

    override def toString = "Interpreter.ConfigBuilder@" + hashCode().toHexString
  }

  private final case class ConfigImpl(imports: ISeq[String], bindings: ISeq[NamedParam],
                                      executor: String, out: Option[Writer], quietImports: Boolean)
    extends Config {

    override def toString = "Interpreter.Config@" + hashCode().toHexString
  }

  /** Creates a new interpreter with the given settings.
    *
    * @param config  the configuration for the interpreter.
    * @return  the new Scala interpreter
    */
  def apply(config: Config = Config().build()): Interpreter = {
    val in = makeIMain(config)
    new Impl(in)
  }

  private trait ResultIntp {
    def interpretWithResult(   line: String, synthetic: Boolean = false): Result
    def interpretWithoutResult(line: String, synthetic: Boolean = false): Result
  }

  private def makeIMain(config: Config): IMain with ResultIntp = {
    val cset = new CompilerSettings()
    cset.classpath.value += File.pathSeparator + sys.props("java.class.path")
    val in = new IMain(cset, new NewLinePrintWriter(config.out getOrElse new ConsoleWriter, true)) with ResultIntp {
      override protected def parentClassLoader = Interpreter.getClass.getClassLoader

      // note `lastRequest` was added in 2.10
      private def _lastRequest = prevRequestList.last

      def interpretWithResult(line: String, synthetic: Boolean): Result = {
        val res0 = interpretWithoutResult(line, synthetic)
        res0 match {
          case Success(name, _) => try {
            Success(name, _lastRequest.lineRep.call("$result"))
          } catch {
            case NonFatal(_) => res0
          }
          case _ => res0
        }
      }

      def interpretWithoutResult(line: String, synthetic: Boolean): Result = {
        interpret(line, synthetic) match {
          case Results.Success    => Success(mostRecentVar, ())
          case Results.Error      => Error("Error") // doesn't work anymore with 2.10.0-M7: _lastRequest.lineRep.evalCaught.map( _.toString ).getOrElse( "Error" ))
          case Results.Incomplete => Incomplete
        }
      }

      //         def interpretWithResult( line: String, synthetic: Boolean, quiet: Boolean ) : Result = {
      //            def loadAndRunReq( req: Request ) = {
      //               loadAndRun( req ) match {
      //                  /** To our displeasure, ConsoleReporter offers only printMessage,
      //                   *  which tacks a newline on the end.  Since that breaks all the
      //                   *  output checking, we have to take one off to balance.
      //                   */
      //                  case Right( result ) =>
      //                     if( !quiet /* printResults */ && result != null ) {
      //                        val resString = result.toString
      //                        reporter.printMessage( resString.stripSuffix( "\n" ))
      //                     } else if( interpreter.isReplDebug ) { // show quiet-mode activity
      //                        val resString = result.toString
      //                        reporter.printMessage( resString.trim.lines.map( "[quiet] " + _ ).mkString( "\n" ))
      //                     }
      //                     // Book-keeping.  Have to record synthetic requests too,
      //                     // as they may have been issued for information, e.g. :type
      //                     recordRequest( req )
      //                     Success( mostRecentVar, result )
      //
      //                  case Left( failure ) =>
      //                     // don't truncate stack traces
      //                     reporter.withoutTruncating( reporter.printMessage( failure ))
      //                     Error( failure )
      //               }
      //            }
      //
      //            if( global == null ) {
      //               Error( "Interpreter not initialized" )
      //            } else {
      //               val reqOption = createRequest( line, synthetic )
      //               reqOption match {
      //                  case Left( result ) => result
      //                  case Right( req )   =>
      //                     // null indicates a disallowed statement type; otherwise compile and
      //                     // fail if false (implying e.g. a type error)
      //                     if( req == null || !req.compile ) {
      //                        Error( "Could not compile code" )
      //                     } else {
      //                        loadAndRunReq(req)
      //                     }
      //               }
      //            }
      //         }

      //         // FUCK why is this private in IMain ???
      //         private def createRequest( line: String, synthetic: Boolean ): Either[ Result, Request ] = {
      //            val content = formatting.indentCode( line )
      //            val trees = parse( content ) match {
      //               case None            => return Left( Incomplete )
      //               case Some( Nil )     => return Left( Error( "Parse error" )) // parse error or empty input
      //               case Some( _trees )  => _trees
      //            }
      //
      //            // If the last tree is a bare expression, pinpoint where it begins using the
      //            // AST node position and snap the line off there.  Rewrite the code embodied
      //            // by the last tree as a ValDef instead, so we can access the value.
      //            trees.last match {
      //               case _: global.Assign => // we don't want to include assignments
      //               case _: global.TermTree | _: global.Ident | _: global.Select => // ... but do want other unnamed terms.
      //                  val varName = if( synthetic ) naming.freshInternalVarName() else naming.freshUserVarName()
      //                  val rewrittenLine = (
      //                     // In theory this would come out the same without the 1-specific test, but
      //                     // it's a cushion against any more sneaky parse-tree position vs. code mismatches:
      //                     // this way such issues will only arise on multiple-statement repl input lines,
      //                     // which most people don't use.
      //                     if (trees.size == 1) "val " + varName + " =\n" + content
      //                     else {
      //                        // The position of the last tree
      //                        val lastpos0 = earliestPosition( trees.last )
      //                        // Oh boy, the parser throws away parens so "(2+2)" is mispositioned,
      //                        // with increasingly hard to decipher positions as we move on to "() => 5",
      //                        // (x: Int) => x + 1, and more.  So I abandon attempts to finesse and just
      //                        // look for semicolons and newlines, which I'm sure is also buggy.
      //                        val (raw1, _ /*raw2*/) = content splitAt lastpos0
      ////                        repldbg("[raw] " + raw1 + "   <--->   " + raw2)
      //
      //                        val adjustment = (raw1.reverse takeWhile (ch => (ch != ';') && (ch != '\n'))).size
      //                        val lastpos = lastpos0 - adjustment
      //
      //                        // the source code split at the laboriously determined position.
      //                        val (l1, l2) = content splitAt lastpos
      ////                        repldbg("[adj] " + l1 + "   <--->   " + l2)
      //
      //                        val prefix   = if (l1.trim == "") "" else l1 + ";\n"
      //                        // Note to self: val source needs to have this precise structure so that
      //                        // error messages print the user-submitted part without the "val res0 = " part.
      //                        val combined   = prefix + "val " + varName + " =\n" + l2
      //
      ////                        repldbg(List(
      ////                           "    line" -> line,
      ////                           " content" -> content,
      ////                           "     was" -> l2,
      ////                           "combined" -> combined) map {
      ////                              case (label, s) => label + ": '" + s + "'"
      ////                           } mkString "\n"
      ////                        )
      //                        combined
      //                     }
      //                  )
      //               // Rewriting    "foo ; bar ; 123"
      //               // to           "foo ; bar ; val resXX = 123"
      //               createRequest( rewrittenLine, synthetic ) match {
      //                  case Right( req )  => return Right(req withOriginalLine line)
      //                  case x             => return x
      //               }
      //               case _ =>
      //            }
      //            Right( new Request( line, trees ))
      //         }
      //
      //         // XXX fuck private
      //         private def safePos( t: global.Tree, alt: Int ): Int = try {
      //            t.pos.startOrPoint
      //         } catch {
      //            case _: UnsupportedOperationException => alt
      //         }
      //
      //         // XXX fuck private
      //         private def earliestPosition( tree: global.Tree ): Int = {
      //            import global._
      //            var pos = Int.MaxValue
      //            tree foreach { t =>
      //               pos = math.min( pos, safePos( t, Int.MaxValue ))
      //            }
      //            pos
      //         }
      //
      //         private def loadAndRun( req: Request ): Either[ String, Any ] = {
      //            if( lineManager == null ) return {
      //               try {
      //                  Right( req.lineRep call naming.sessionNames.print )
      //               }
      //               catch {
      //                  case ex: Throwable => Left( req.lineRep.bindError( ex ))
      //               }
      //            }
      //            import interpreter.Line._
      //
      //            try {
      //               val execution = lineManager.set( req.originalLine )(
      ////                  try {
      //                     req.lineRep call naming.sessionNames.print
      ////                  } catch {
      ////                     case np: NullPointerException => ()
      ////                  }
      //               )
      //               execution.await()
      //               execution.state match {
      ////                  case Done       => Right( execution.get() )
      //                  case Done       => execution.get(); Right( req.lineRep.call( "$result" ))
      //                  case Threw      => Left( req.lineRep.bindError( execution.caught() ))
      //                  case Cancelled  => Left( "Execution interrupted by signal.\n" )
      //                  case Running    => Left( "Execution still running! Seems impossible." )
      //               }
      //            }
      //            finally {
      //               lineManager.clear()
      //            }
      //         }
    }

    in.setContextClassLoader()
    config.bindings.foreach(in.bind)
    if (config.quietImports) in.quietImport(config.imports: _*) else in.addImports(config.imports: _*)
    in.setExecutionWrapper(config.executor)
    in
  }

  def async(config: Config = Config().build())(done: Interpreter => Unit): Unit = {
    val exec = Executors.newSingleThreadExecutor()
    exec.submit(new Runnable {
      def run(): Unit = {
        val res = apply(config)
        done(res)
      }
    })
  }

  private final class Impl(in: IMain with ResultIntp) extends Interpreter {
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
          //                    val ihs   = intp.importHandlers.headOption
          //                    ihs.foreach { ih =>
          //                      println("---imported symbols---")
          //                      ih.importedSymbols.foreach(println)
          //                      println(s"isLegalTopLevel? ${ih.isLegalTopLevel} isPredefImport? ${ih.isPredefImport} importsWildcard? ${ih.importsWildcard}")
          //                      println(s"importString: ${ih.importString}")
          //                      println(s"targetType: ${ih.targetType}")
          //                      println("---imported names---")
          //                      ih.importedNames.foreach(println)
          //                      println("---selectors---")
          //                      ih.selectors.foreach(println)
          //                      println("---wildcard names---")
          //                      ih.wildcardNames.foreach(println)
          //                    }
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

          intp.global.definitions
          // val global  = intp.global
          val rm      = global.rootMirror
          val testPck = rm.getPackage(global.newTermNameCached("scala.concurrent"))
          val testCmp = new PackageCompletion(testPck.tpe)

          // val res = topLevelBase ++ imported
          // res.foreach(println)
          val add: List[CompletionAware] = testCmp :: Nil // CompletionAware(() => intp.importedTypes.map(_.decode)) :: Nil
          val res = sup ++ add // .map(TypeMemberCompletion.imported)
          res
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
