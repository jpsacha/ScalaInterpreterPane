/*
 *  MakeIMain.scala
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
package impl

import java.io.Writer
import java.io.PrintStream
import java.io.OutputStream
import dotty.tools.repl.ReplDriver
import dotty.tools.repl.Rendering
import dotty.tools.repl.IMainImpl

object MakeIMain {
  def apply(config: Interpreter.Config): IntpInterface = {
    val out     = config.out.fold(Console.out)(wr => new PrintStream(new WriterOutputStream(wr)))
    val loader  = getClass.getClassLoader
    new IMainImpl(out, loader)
  }
  
  private final class WriterOutputStream(wr: Writer) extends OutputStream {
    override def write(b: Array[Byte], off: Int, len: Int): Unit = {
      val str = new String(b, off, len)
      wr.write(str, 0, str.length)
    }

    def write(b: Int): Unit = write(Array(b.toByte), 0, 1)
  }
}