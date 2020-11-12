/*
 *  ScalaInterpreterPane.scala
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

import de.sciss.scalainterpreter.impl.{InterpreterPaneImpl => Impl}
import javax.swing.KeyStroke

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.swing.Component

object InterpreterPane {
  object Config {
    implicit def build(b: ConfigBuilder): Config = b.build

    def apply(): ConfigBuilder = Impl.newConfigBuilder()
  }

  sealed trait ConfigLike {
    /** Key stroke to trigger interpreter execution of selected text */
    def executeKey: KeyStroke

    /** Code to initially execute once the interpreter is initialized. */
    def code: String

    /** Whether to prepend an information text with the execution key info to the code pane's text */
    def prependExecutionInfo: Boolean
  }

  trait Config extends ConfigLike

  object ConfigBuilder {
    def apply(config: Config): ConfigBuilder = Impl.mkConfigBuilder(config)
  }

  trait ConfigBuilder extends ConfigLike {
    var executeKey          : KeyStroke
    var code                : String
    var prependExecutionInfo: Boolean

    def build: Config
  }

  def wrap(interpreter: Interpreter, codePane: CodePane): InterpreterPane = Impl.wrap(interpreter, codePane)

  def wrapAsync(interpreter: Future[Interpreter], codePane: CodePane)
               (implicit exec: ExecutionContext = Interpreter.defaultInitializeContext): InterpreterPane =
    Impl.wrapAsync(interpreter, codePane)

  def apply(config: Config = Config().build,
            interpreterConfig : Interpreter .Config = Interpreter .Config().build,
            codePaneConfig    : CodePane    .Config = CodePane    .Config().build)
           (implicit exec: ExecutionContext = Interpreter.defaultInitializeContext): InterpreterPane =
    Impl.apply(config, interpreterConfig, codePaneConfig)

  def bang(codePane: CodePane, interpreter: Interpreter): Option[Interpreter.Result] =
    Impl.bang(codePane, interpreter)
}
trait InterpreterPane {
  def component: Component

  def codePane: CodePane

  var status: String

  def clearStatus(): Unit

  def setStatus(result: Interpreter.Result): Unit

  def interpret(code: String): Option[Interpreter.Result]
}
