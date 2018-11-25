package de.sciss.scalainterpreter

object Completion {
  final case class Result(cursor: Int, candidates: List[Candidate])

  val NoResult = Result(-1, Nil)

  trait Candidate extends Proxy {
    def stringRep: String

    def self: Any = stringRep
  }
}
trait Completion {

}
