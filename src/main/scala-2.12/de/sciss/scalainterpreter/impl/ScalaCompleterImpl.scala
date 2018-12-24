/*
 * Scala (https://www.scala-lang.org)
 *
 * Copyright EPFL and Lightbend, Inc.
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package de.sciss.scalainterpreter.impl

import scala.tools.nsc.interpreter.IMain

class ScalaCompleterImpl(_intp: IMain) extends AbstractScalaCompleter(_intp) {
  private val Cursor = IMain.DummyCursorFragment + " "

  protected def presentationCompile(cursor: Int, buf: String): Option[intp.PresentationCompileResult] = {
    val bufMarked = buf.patch(cursor, Cursor, 0)
    val either = PeekNSC.presentationCompile(intp)(bufMarked)
    either.toOption
  }
}
