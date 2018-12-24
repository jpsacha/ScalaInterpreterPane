package de.sciss.scalainterpreter

object Completion {
  final case class Result(cursor: Int, candidates: List[Candidate])

  val NoResult = Result(-1, Nil)

  sealed trait Candidate /* extends Proxy */ {
    def name: String

    def fullString: String

//    override def toString: String = fullString

    // def self: Any = stringRep
  }

  final case class Simple(name: String) extends Candidate {
    def fullString: String = name
  }

  final case class Def(name: String, info: String = "", isModule: Boolean = false) extends Candidate {
    def fullString: String = s"$name$info"

    override def toString: String = {
      val sInfo   = if (info.nonEmpty) s""", info = "$info"""" else ""
      val sIsMod  = if (isModule) ", isModule = true" else ""
      s"""Completion.$productPrefix("$name"$sInfo$sIsMod)"""
    }
  }
}
