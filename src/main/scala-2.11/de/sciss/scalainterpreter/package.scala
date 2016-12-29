/*
 *  package.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss

import tools.nsc.interpreter

package object scalainterpreter {
  object NamedParam {
    def apply[A: Manifest](name: String, value: A): NamedParam = interpreter.NamedParam(name, value)
  }

  type NamedParam = interpreter.NamedParam

  // Scala version specific
  type Completer = tools.nsc.interpreter.Completion.ScalaCompleter
}