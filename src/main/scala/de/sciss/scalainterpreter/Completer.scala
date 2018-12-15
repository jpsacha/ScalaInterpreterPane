package de.sciss.scalainterpreter

trait Completer {
  def complete(buffer: String, cursor: Int): Completion.Result
}
