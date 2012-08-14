package de.sciss

import tools.nsc.interpreter

package object scalainterpreter {
   object NamedParam {
      def apply[ A : Manifest ]( name: String, value: A ) : NamedParam = interpreter.NamedParam( name, value )
   }
   type NamedParam = interpreter.NamedParam
}