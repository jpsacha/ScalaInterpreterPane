package scala

import scala.tools.nsc.interpreter.{IR, PresentationCompilation}

object PeekNSC {
  def presentationCompile(p: PresentationCompilation)(line: String): Either[IR.Result, p.PresentationCompileResult] =
    p.presentationCompile(line)
}
