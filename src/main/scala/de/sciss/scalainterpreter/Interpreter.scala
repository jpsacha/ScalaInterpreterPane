/*
 *  Interpreter.scala
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

import java.io.Writer
import java.util.concurrent.Executors

import de.sciss.scalainterpreter.impl.{InterpreterImpl => Impl}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.language.implicitConversions

/** The `Interpreter` wraps the underlying Scala interpreter functionality. */
object Interpreter {
  val defaultInitializeContext: ExecutionContext =
    ExecutionContext fromExecutorService Executors.newSingleThreadExecutor()

  /** Factory object for creating new configuration builders. */
  object Config {
    /** A configuration builder is automatically converted to an immutable configuration */
    implicit def build(b: ConfigBuilder): Config = b.build

    /** Creates a new configuration builder with defaults. */
    def apply(): ConfigBuilder = Impl.newConfigBuilder()
  }
  sealed trait ConfigLike {
    implicit def build(b: ConfigBuilder): Config = b.build

    /** A list of package names to import to the scope of the interpreter. */
    def imports: ISeq[String]

    /** A list of bindings which make objects in the hosting environment available to the interpreter under a
      * given name.
      */
    def bindings: ISeq[(String, Any)]

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
    def out: Option[Writer]

    /** Whether initial imports should be performed silently (`true`) or not (`false`). Not silent means the imported
      * packages' names will be printed to the default printing device (`out`).
      */
    def quietImports: Boolean
  }

  /** Configuration for an interpreter. */
  trait Config extends ConfigLike

  object ConfigBuilder {
    /** Creates a new configuration builder initialized to the values taken from an existing configuration.
      *
      * @param config  the configuration from which to take the initial settings
      * @return        the new mutable configuration builder
      */
    def apply(config: Config): ConfigBuilder = Impl.mkConfigBuilder(config)
  }

  trait ConfigBuilder extends ConfigLike {
    var imports     : ISeq[String]
    var bindings    : ISeq[(String, Any)]
    var executor    : String
    var out         : Option[Writer]
    var quietImports: Boolean

    def build: Config
  }

  sealed trait Result {
    def isSuccess: Boolean
  }
  final case class Success(resultName: String, resultValue: Any) extends Result {
    def isSuccess = true
  }
  // can't find a way to get the exception right now
  final case class Error(message: String) extends Result {
    def isSuccess = false
  }
  case object Incomplete extends Result {
    def isSuccess = false
  }

  /** Creates a new interpreter with the given settings.
    *
    * @param config  the configuration for the interpreter.
    * @return  the new Scala interpreter
    */
  def apply(config: Config = Config().build): Interpreter = Impl(config)

  /** Convenience constructor with calls `apply` inside a blocking future. */
  def async(config: Config = Config().build)
           (implicit exec: ExecutionContext = defaultInitializeContext): Future[Interpreter] = Future {
    blocking(apply(config))
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
  def interpretWithResult(code: String, quiet: Boolean = false): Interpreter.Result

  /** Interprets a piece of code. Unlike `interpret` the result is not evaluated. That is, in the case
    * off `Success` the result value will always be `()`.
    */
  def interpret(code: String, quiet: Boolean = false): Interpreter.Result

  /** A code completion component which may be attached to an editor. */
  def completer: Completer
}
