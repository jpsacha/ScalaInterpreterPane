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

import javax.swing.{AbstractAction, Box, JComponent, JLabel, JPanel, JProgressBar, KeyStroke, OverlayLayout}
import java.awt.event.{ActionEvent, InputEvent, KeyEvent}
import java.awt.{EventQueue, BorderLayout}

//object ScalaInterpreterPane {
//   val name          = "ScalaInterpreterPane"
//   val version       = "1.0.0-SNAPSHOT"
//   val copyright     = "(C)opyright 2010-2012 Hanns Holger Rutz"
//}

object InterpreterPane {
   object Config {
      implicit def build( b: ConfigBuilder ) : Config = b.build
      def apply() : ConfigBuilder = new ConfigBuilderImpl
   }
   sealed trait ConfigLike {
      /**
       * Key stroke to trigger interpreter execution of selected text
       */
      def executeKey: KeyStroke

      /**
       * Code to initially execute once the interpreter is initialized.
       */
      def code: String

      /**
       * Whether to prepend an information text with the execution key info
       * to the code pane's text
       */
      def prependExecutionInfo : Boolean
   }
   sealed trait Config extends ConfigLike
   object ConfigBuilder {
      def apply( config: Config ) : ConfigBuilder = {
         import config._
         val b = new ConfigBuilderImpl
         b.executeKey = executeKey
         b.code = code
         b.prependExecutionInfo = prependExecutionInfo
         b
      }
   }
   sealed trait ConfigBuilder extends ConfigLike {
      def executeKey: KeyStroke // need to restate that to get reassignment sugar
      def executeKey_=( value: KeyStroke ) : Unit
      def code: String // need to restate that to get reassignment sugar
      def code_=( value: String ) : Unit
      def prependExecutionInfo : Boolean // need to restate that to get reassignment sugar
      def prependExecutionInfo_=( value: Boolean ) : Unit

      // subclasses may override this
      var initialCode: Option[ String ] = None
      def build: Config
   }

   private final class ConfigBuilderImpl extends ConfigBuilder {
      var executeKey = KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK )
      var code       = ""
      var prependExecutionInfo = true
      def build : Config = ConfigImpl( executeKey, code, prependExecutionInfo )
      override def toString = "InterpreterPane.ConfigBuilder@" + hashCode().toHexString
   }

   private final case class ConfigImpl( executeKey: KeyStroke, code: String, prependExecutionInfo: Boolean )
   extends Config {
      override def toString = "InterpreterPane.Config@" + hashCode().toHexString
   }

   private def incorporate( config: Config, code: CodePane.Config ) : CodePane.Config = {
      val res = CodePane.ConfigBuilder( code )
      res.text = "// Type Scala code here.\n// Press '" +
         KeyEvent.getKeyModifiersText( config.executeKey.getModifiers ) + " + " +
         KeyEvent.getKeyText( config.executeKey.getKeyCode ) + "' to execute selected text\n// or current line.\n\n" + res.text
      res.build
   }

   def wrap( interpreter: Interpreter, codePane: CodePane ) : InterpreterPane =
      new Impl( Config().build, Some( interpreter ), codePane )

   def apply( config: Config = Config().build,
              interpreterConfig: Interpreter.Config = Interpreter.Config().build,
              codePaneConfig: CodePane.Config = CodePane.Config().build ) : InterpreterPane = {

      val cpSet      = if( config.prependExecutionInfo ) incorporate( config, codePaneConfig ) else codePaneConfig
      val codePane   = CodePane( cpSet )
      val impl       = new Impl( config, None, codePane )
      Interpreter.async( interpreterConfig ) { in =>
         EventQueue.invokeLater( new Runnable {
            def run() {
               impl.setInterpreter( in )
            }
         })
      }
      impl
   }

   private final class Impl( config: Config, private var interpreter: Option[ Interpreter ], codePane: CodePane )
   extends InterpreterPane {
      private def checkInterpreter() {
         val has = interpreter.isDefined
         codePane.editor.setEnabled( has )
         ggProgressInvis.setVisible( has )
         ggProgress.setVisible( !has )
         if( config.code != "" ) interpreter.foreach( _.interpret( config.code ))
         status = if( has ) "Ready." else "Initializing..."
      }

      def setInterpreter( in: Interpreter ) {
         require( interpreter.isEmpty )
         interpreter = Some( in )
//         codePane.init()
         codePane.installAutoCompletion( in )
         codePane.editor.requestFocus()
         checkInterpreter()
      }

      private val ggStatus = {
         val lb = new JLabel( "" )
         lb.putClientProperty( "JComponent.sizeVariant", "small" )
         lb
      }
      private val ggProgress = {
         val p = new JProgressBar() {
            override def getPreferredSize = {
               val d = super.getPreferredSize
               d.width = math.min( 32, d.width )
               d
            }
            override def getMaximumSize = {
               val d = super.getMaximumSize
               d.width = math.min( 32, d.width )
               d
            }
         }
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

      val component = {
         val p = new JPanel( new BorderLayout() )
         p.add( codePane.component, BorderLayout.CENTER )
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
            status = in.interpretWithoutResult( code ) match {
               case Interpreter.Success( name, _ ) =>
                  "Ok. <" + name + ">"
               case Interpreter.Error( message ) =>
                  "! Error : " + message
               case Interpreter.Incomplete =>
                  "! Code incomplete"
            }
         }
      }

      def installExecutionAction() {
         val ed   = codePane.editor
         val imap = ed.getInputMap( JComponent.WHEN_FOCUSED )
         val amap = ed.getActionMap
         imap.put( config.executeKey, "de.sciss.exec" )
         amap.put( "de.sciss.exec", new AbstractAction {
            def actionPerformed( e: ActionEvent ) {
               codePane.getSelectedTextOrCurrentLine.foreach( interpret )
            }
         })
      }

      checkInterpreter()
      installExecutionAction()
   }
}
sealed trait InterpreterPane {
   def component: JComponent
   def status: String
   def status_=( value: String ) : Unit

   def interpret( code: String ) : Unit
}
