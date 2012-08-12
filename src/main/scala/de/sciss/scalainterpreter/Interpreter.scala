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

object Interpreter {
   object Settings {
      def apply() : SettingsBuilder = new SettingsBuilderImpl
   }
   sealed trait Settings {
      implicit def fromBuilder( b: SettingsBuilder ) : Settings = b.build
      def imports: Seq[ String ]
      def bindings: Seq[ NamedParam ]
      def out: Option[ Writer ]
   }
   sealed trait SettingsBuilder extends Settings {
      def imports_=( value: Seq[ String ]) : Unit
      def bindings_=( value: Seq[ NamedParam ]) : Unit
      def out_=( value: Option[ Writer ]) : Unit

      def build: Settings
   }

   sealed trait Result
   case class Success( result: Any ) extends Result
   case object Error extends Result // can't find a way to get the exception right now
   case object Incomplete extends Result

   private final class SettingsBuilderImpl extends SettingsBuilder {
      var imports    = Seq.empty[ String ]
      var bindings   = Seq.empty[ NamedParam ]
      var out        = Option.empty[ Writer ]

      def build : Settings = new SettingsImpl( imports, bindings, out )
      override def toString = "Interpreter.SettingsBuilder@" + hashCode().toHexString
   }

   private final case class SettingsImpl( imports: Seq[ String ], bindings: Seq[ NamedParam ], out: Option[ Writer ])
   extends Settings {
      override def toString = "Interpreter.Settings@" + hashCode().toHexString
   }

   def apply( settings: Settings = Settings().build ) : Interpreter = {
      val cset = new CompilerSettings()
      cset.classpath.value += File.pathSeparator + System.getProperty( "java.class.path" )
      val in = new IMain( cset, new NewLinePrintWriter( settings.out getOrElse (new ConsoleWriter), true )) {
         override protected def parentClassLoader = Interpreter.getClass.getClassLoader
      }

      in.setContextClassLoader()
      settings.bindings.foreach( in.bind )
      in.addImports( settings.imports: _* )

//      initialCode.foreach( in.interpret( _ ))

      new Impl( in )
   }

   private final class Impl( in: IMain ) extends Interpreter {
      private val cmp = new JLineCompletion( in )

      override def toString = "Interpreter@" + hashCode().toHexString

      def completer: Completion.ScalaCompleter = cmp.completer()

      def interpret( code: String ) : Interpreter.Result = {
// requestFromLine is private :-(

//         val synthetic = false
//
//         def loadAndRunReq( req: in.Request ) : Results.Result = {
//            val (result, succeeded) = req.loadAndRun
//
//            /** To our displeasure, ConsoleReporter offers only printMessage,
//             *  which tacks a newline on the end.  Since that breaks all the
//             *  output checking, we have to take one off to balance.
//             */
//            if( succeeded ) {
//               if( printResults && result != "" ) {
//                  printMessage( result.stripSuffix( "\n" ))
//               } else if( isReplDebug ) { // show quiet-mode activity
//                  printMessage( result.trim.lines.map( "[quiet] " + _ ).mkString( "\n" ))
//               }
//
//               // Book-keeping.  Have to record synthetic requests too,
//               // as they may have been issued for information, e.g. :type
//               recordRequest( req )
//               Results.Success
//            } else {
//               // don't truncate stack traces
//               withoutTruncating( printMessage( result ))
//               Results.Error
//            }
//         }
//
//         if( global == null ) Results.Error
//         else requestFromLine( code, synthetic ) match {
//            case Left( result )  => result
//            case Right( req )    =>
//               // null indicates a disallowed statement type; otherwise compile and
//               // fail if false (implying e.g. a type error)
//               if( req == null || !req.compile ) Results.Error
//               else loadAndRunReq( req )
//         }

         in.interpret( code ) match {
            case Results.Success =>
               val res = in.valueOfTerm( in.mostRecentVar ).getOrElse( () )
               Interpreter.Success( res )
            case Results.Error =>
//               in.quietRun()
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