/*
 *  Interpreter.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2012 Hanns Holger Rutz. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.scalainterpreter

import tools.nsc.{ConsoleWriter, NewLinePrintWriter, Settings => CompilerSettings}
import java.io.{Writer, File}
import tools.nsc.interpreter.{JLineCompletion, Completion, Results, NamedParam, IMain}
import java.util.concurrent.Executors

object Interpreter {
   object Config {
      implicit def build( b: ConfigBuilder ) : Config = b.build
      def apply() : ConfigBuilder = new ConfigBuilderImpl
   }
   sealed trait ConfigLike {
      implicit def build( b: ConfigBuilder ) : Config = b.build
      def imports: Seq[ String ]
      def bindings: Seq[ NamedParam ]
      def out: Option[ Writer ]
   }
   sealed trait Config extends ConfigLike
   object ConfigBuilder {
      def apply( config: Config ) : ConfigBuilder = {
         import config._
         val b = new ConfigBuilderImpl
         b.imports = imports
         b.bindings = bindings
         b.out = out
         b
      }
   }
   sealed trait ConfigBuilder extends ConfigLike {
      def imports: Seq[ String ] // need to restate that to get reassignment sugar
      def imports_=( value: Seq[ String ]) : Unit
      def bindings: Seq[ NamedParam ] // need to restate that to get reassignment sugar
      def bindings_=( value: Seq[ NamedParam ]) : Unit
      def out: Option[ Writer ] // need to restate that to get reassignment sugar
      def out_=( value: Option[ Writer ]) : Unit

      def build: Config
   }

   sealed trait Result
   case class Success( resultName: String, resultValue: Any ) extends Result
   case object Error extends Result // can't find a way to get the exception right now
   case object Incomplete extends Result

   private final class ConfigBuilderImpl extends ConfigBuilder {
      var imports    = Seq.empty[ String ]
      var bindings   = Seq.empty[ NamedParam ]
      var out        = Option.empty[ Writer ]

      def build : Config = new ConfigImpl( imports, bindings, out )
      override def toString = "Interpreter.ConfigBuilder@" + hashCode().toHexString
   }

   private final case class ConfigImpl( imports: Seq[ String ], bindings: Seq[ NamedParam ], out: Option[ Writer ])
   extends Config {
      override def toString = "Interpreter.Config@" + hashCode().toHexString
   }

   def apply( config: Config = Config().build ) : Interpreter = {
      val in = makeIMain( config )
      new Impl( in )
   }

   private def makeIMain( config: Config ) : IMain = {
      val cset = new CompilerSettings()
      cset.classpath.value += File.pathSeparator + System.getProperty( "java.class.path" )
      val in = new IMain( cset, new NewLinePrintWriter( config.out getOrElse (new ConsoleWriter), true )) {
         override protected def parentClassLoader = Interpreter.getClass.getClassLoader
      }

      in.setContextClassLoader()
      config.bindings.foreach( in.bind )
      in.addImports( config.imports: _* )
      in
   }

   def async( config: Config = Config().build )( done: Interpreter => Unit ) {
      val exec = Executors.newSingleThreadExecutor()
      exec.submit( new Runnable {
         def run() {
            val res = apply( config )
            done( res )
         }
      })
   }

   private final class Impl( in: IMain ) extends Interpreter {
      private val cmp = new JLineCompletion( in )

      override def toString = "Interpreter@" + hashCode().toHexString

      def completer: Completion.ScalaCompleter = cmp.completer()

      def interpret( code: String ) : Interpreter.Result = {
         in.interpret( code ) match {
            case Results.Success =>
               val resName = in.mostRecentVar
//               val reqOpt  = in.requestForIdent( resName )
//               val resOpt  = reqOpt.flatMap { req =>
//                  req.getEval
//               }
//               val resVal  = resOpt.getOrElse( () )
               val resVal  = in.valueOfTerm( resName ).getOrElse( () )
               Interpreter.Success( resName, resVal )
            case Results.Error =>
               Interpreter.Error
            case Results.Incomplete =>
               Interpreter.Incomplete
         }
      }
   }
}
trait Interpreter {
   def interpret( code: String ) : Interpreter.Result
   def completer: Completion.ScalaCompleter
}