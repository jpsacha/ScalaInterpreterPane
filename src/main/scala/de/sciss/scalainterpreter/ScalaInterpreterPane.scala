/*
 *  ScalaInterpreterPane.scala
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

import actions.CompletionAction
import javax.swing.{ AbstractAction, Box, JComponent, JEditorPane, JLabel, JPanel, JProgressBar, JScrollPane,
   KeyStroke, OverlayLayout, ScrollPaneConstants, SwingWorker }
import ScrollPaneConstants._

import jsyntaxpane.{ DefaultSyntaxKit, SyntaxDocument }
import tools.nsc.{ ConsoleWriter, NewLinePrintWriter, Settings }
import java.io.{ File, Writer }
import java.awt.event.{InputEvent, ActionEvent, KeyEvent}
import tools.nsc.interpreter.{NamedParam, JLineCompletion, Results, IMain}
//import jsyntaxpane.syntaxkits.ScalaSyntaxKit
import java.awt.{Color, BorderLayout}

//object ScalaInterpreterPane {
//   val name          = "ScalaInterpreterPane"
//   val version       = "1.0.0-SNAPSHOT"
//   val copyright     = "(C)opyright 2010-2012 Hanns Holger Rutz"
//}

object InterpreterPane {
   object Settings {
      implicit def fromBuilder( b: SettingsBuilder ) : Settings = b.build
      def apply() : SettingsBuilder = new SettingsBuilderImpl
   }
   sealed trait Settings {
      /**
       * Key stroke to trigger interpreter execution of selected text
       */
      def executeKey: KeyStroke

      /**
       * Code to initially execute once the interpreter is initialized.
       */
      def code: String
   }
   sealed trait SettingsBuilder extends Settings {
      def executeKey_=( value: KeyStroke ) : Unit
      def code_=( value: String ) : Unit

      // subclasses may override this
      var initialCode: Option[ String ] = None
      def build: Settings
   }

   private final class SettingsBuilderImpl extends SettingsBuilder {
      var executeKey = KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK )
      var code       = ""
      def build : Settings = SettingsImpl( executeKey, code )
      override def toString = "InterpreterPane.SettingsBuilder@" + hashCode().toHexString
   }

   private final case class SettingsImpl( executeKey: KeyStroke, code: String ) extends Settings {
      override def toString = "InterpreterPane.Settings@" + hashCode().toHexString
   }

   def defaultCodePaneSettings( settings: Settings ) : CodePane.SettingsBuilder = {
      val res = CodePane.Settings()
      res.text = "// Type Scala code here.\n// Press '" +
         KeyEvent.getKeyModifiersText( settings.executeKey.getModifiers ) + " + " +
         KeyEvent.getKeyText( settings.executeKey.getKeyCode ) + "' to execute selected text\n// or current line.\n"
      res
   }

   def wrap( interpreter: Interpreter, codePane: CodePane ) : InterpreterPane =
      new Impl( Some( interpreter ), codePane )

   def apply( settings: Settings = Settings().build )(
              interpreterSettings: Interpreter.Settings = Interpreter.Settings().build,
              codePaneSettings: CodePane.Settings = defaultCodePaneSettings( settings ).build ) : InterpreterPane = {

      val codePane   = CodePane( codePaneSettings )
      val impl       = new Impl( None, codePane )
      impl.status    = "Initializing..."
      impl
   }

   private final class Impl( interpreter: Option[ Interpreter ], codePane: CodePane )
   extends InterpreterPane {
      private val ggStatus = {
         val lb = new JLabel( "" )
         lb.putClientProperty( "JComponent.sizeVariant", "small" )
         lb
      }
      private val ggProgress = {
         val p = new JProgressBar()
         p.putClientProperty( "JProgressBar.style", "circular" )
         p.setIndeterminate( true )
         p
      }
      private val ggProgressInvis = {
         val p = new JComponent {
            override def getMinimumSize   = ggProgress.getMinimumSize
            override def getPreferredSize = ggProgress.getPreferredSize
            override def getMaximumSize   = ggProgress.getMaximumSize
         }
         p.setVisible( false )
         p
      }
      private val progressPane = {
         val p = new JPanel()
         p.setLayout( new OverlayLayout( p ))
         p.add( ggProgress )
         p.add( ggProgressInvis )
         p
      }

      private val statusPane = {
         val b = Box.createHorizontalBox()
         b.add( Box.createHorizontalStrut( 4 ))
         b.add( progressPane )
         b.add( Box.createHorizontalStrut( 4 ))
         b.add( ggStatus )
         b
      }

      private val ggScroll = {
         val editor = codePane.component
         editor.setEnabled( interpreter.isDefined )
         new JScrollPane( editor, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_ALWAYS  )
      }

      val component = {
         val p = new JPanel( new BorderLayout() )
         p.add( ggScroll, BorderLayout.CENTER )
         p.add( statusPane, BorderLayout.SOUTH )
         p
      }

      def status: String = {
         val res = ggStatus.getText
         if( res == null ) "" else res
      }

      def status_=( value: String ) { ggStatus.setText( value )}

      def interpret( code: String ) {
         interpreter.foreach { in =>
            status = ""
            in.interpret( code ) match {
               case Interpreter.Success( name, _ ) => status = "Ok. <" + name + ">"
               case Interpreter.Error              => status = "! Error !"
               case Interpreter.Incomplete         => status = "! Code incomplete !"
            }
         }
      }
   }
}
sealed trait InterpreterPane {
   def component: JComponent
   def status: String
   def status_=( value: String ) : Unit

   def interpret( code: String ) : Unit
}


//      // spawn interpreter creation
//      (new SwingWorker[ Unit, Unit ] {
//         override def doInBackground() {
//            initialCode.foreach( in.interpret( _ ))
//         }
//
//         override protected def done() {
//            ggProgressInvis.setVisible( true )
//            ggProgress.setVisible( false )
//            editorPane.setContentType( "text/scala" )
//            editorPane.setText( initialText )
//            docVar = editorPane.getDocument match {
//               case sdoc: SyntaxDocument => Some( sdoc )
//               case _ => None
//            }
//
//            editorPane.setFont( createFont )
//            editorPane.setEnabled( true )
//            editorPane.requestFocus()
//            status( "Ready." )
//         }
//      }).execute()
