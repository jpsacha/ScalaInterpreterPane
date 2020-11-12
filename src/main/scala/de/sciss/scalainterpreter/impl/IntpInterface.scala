package de.sciss.scalainterpreter
package impl

trait IntpInterface {
  def interpretWithResult(   line: String, synthetic: Boolean = false, quiet: Boolean = false): Interpreter.Result
  def interpretWithoutResult(line: String, synthetic: Boolean = false, quiet: Boolean = false): Interpreter.Result

  def bind(tup: (String, Any)): Unit

  def setExecutionWrapper(code: String): Unit
  def clearExecutionWrapper(): Unit

  def mkCompleter(): Completer
}