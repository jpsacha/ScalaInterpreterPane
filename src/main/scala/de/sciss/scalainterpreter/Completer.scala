package de.sciss.scalainterpreter

trait Completer {
  def complete(buffer: String, cursor: Int, tabCount: Int = -1): Completion.Result
}
