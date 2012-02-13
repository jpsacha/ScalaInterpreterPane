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

object ScalaInterpreterPane {
   val name          = "ScalaInterpreterPane"
   val version       = 0.21
   val isSnapshot    = true
   val copyright     = "(C)opyright 2010-2012 Hanns Holger Rutz"

   def versionString = {
      val s = (version + 0.001).toString.substring( 0, 4 )
      if( isSnapshot ) s + "-SNAPSHOT" else s
   }
}

class ScalaInterpreterPane
extends JPanel with CustomizableFont {
   pane =>

   @volatile private var compVar: Option[ JLineCompletion ] = None
//   @volatile private var interpreterVar: Option[ IMain ] = None
   private var docVar: Option[ SyntaxDocument ] = None

   // subclasses may override this
//   var executeKeyStroke = {
//      val ms = Toolkit.getDefaultToolkit.getMenuShortcutKeyMask
//      KeyStroke.getKeyStroke( KeyEvent.VK_E, if( ms == InputEvent.CTRL_MASK ) ms | InputEvent.SHIFT_MASK else ms )
//   }
   var executeKeyStroke = KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK )

   // subclasses may override this
   var initialCode: Option[ String ] = None

   // subclasses may override this
   var out: Option[ Writer ] = None

   var customKeyMapActions:    Map[ KeyStroke, () => Unit ] = Map.empty
   var customKeyProcessAction: Option[ KeyEvent => KeyEvent ] = None
   /**
    *    Subclasses may override this to
    *    create initial bindings for the interpreter.
    *    Note that this is not necessarily executed
    *    on the event thread.
    */
   var customBindings = Seq.empty[ NamedParam ] // Option[ IMain => Unit ] = None
   var customImports  = Seq.empty[ String ]

   var initialText = """// Type Scala code here.
// Press '""" + KeyEvent.getKeyModifiersText( executeKeyStroke.getModifiers ) + " + " +
      KeyEvent.getKeyText( executeKeyStroke.getKeyCode ) + """' to execute selected text
// or current line.
"""

   private val ggStatus = new JLabel( "Initializing..." )

   protected val editorPane = {
      val res = new JEditorPane() {
         override protected def processKeyEvent( e: KeyEvent ) {
            super.processKeyEvent( customKeyProcessAction.map( fun => {
               fun.apply( e )
            }) getOrElse e )
         }
      }
      res.setBackground( new Color( 0x14, 0x1F, 0x2E ))  // stupid... this cannot be set in the kit config
      res.setForeground( new Color( 0xF5, 0xF5, 0xF5 ))
      res.setSelectedTextColor( new Color( 0xF5, 0xF5, 0xF5 ))
      res
   }
   private val progressPane      = new JPanel()
   private val ggProgress        = new JProgressBar()
   private val ggProgressInvis   = new JComponent {
      override def getMinimumSize   = ggProgress.getMinimumSize
      override def getPreferredSize = ggProgress.getPreferredSize
      override def getMaximumSize   = ggProgress.getMaximumSize
   }

   def interpreter: Option[ IMain ] = compVar.map( _.intp )
   def doc: Option[ SyntaxDocument ] = docVar

   def init() {
      // spawn interpreter creation
      (new SwingWorker[ Unit, Unit ] {
         override def doInBackground() {
            val settings = {
               val set = new Settings()
               set.classpath.value += File.pathSeparator + System.getProperty( "java.class.path" )
               set
            }

//            val cfg = DefaultSyntaxKit.getConfig( classOf[ DefaultSyntaxKit ])
//            cfg.put( "Action.completion", "de.sciss.scalainterpreter.actions.CompletionAction, control SPACE" )
            DefaultSyntaxKit.initKit()
            DefaultSyntaxKit.registerContentType( "text/scala", "de.sciss.scalainterpreter.ScalaSyntaxKit" )
            val synCfg = DefaultSyntaxKit.getConfig( classOf[ ScalaSyntaxKit ])
            // this is currently wrong in the config.properties!
//            synCfg.put( "Action.toggle-comments.LineComments", "// " )
            // colors
            synCfg.put( "Style.DEFAULT",  "0xf5f5f5, 0" )
            synCfg.put( "Style.KEYWORD",  "0x0099ff, 1" )
            synCfg.put( "Style.OPERATOR", "0xf5f5f5, 0" )
            synCfg.put( "Style.COMMENT",  "0x50f050, 2" )   // XXX somewhat appears too dark
//            synCfg.put( "Style.COMMENT2", "0x50f050, 2" )
            synCfg.put( "Style.NUMBER",   "0xff8080, 0" )
            synCfg.put( "Style.STRING",   "0xa0ffa0, 0" )
            synCfg.put( "Style.STRING2",  "0xa0ffa0, 0" )
            synCfg.put( "Style.IDENTIFIER",  "0xf5f5f5, 0" )
//            synCfg.put( "Style.DELIMITER", "0xff0000, 0" )
            synCfg.put( "Style.TYPE",      "0x91ccff, 0" )
            synCfg.put( "LineNumbers.CurrentBack", "0x1b2b40" )
            synCfg.put( "LineNumbers.Foreground", "0x808080" )
//            synCfg.put( "LineNumbers.Background", "141f2e" ) // XXX has no effect
            synCfg.put( "SingleColorSelect", "true" )
            synCfg.put( "SelectionColor", "0x375780" )
            synCfg.put( "CaretColor", "0xffffff" )
            synCfg.put( "PairMarker.Color", "0x3c5f8c" )
//            synCfg.put( "TextAA", ... )   // XXX has no effect

            val in = new IMain( settings, new NewLinePrintWriter( out getOrElse (new ConsoleWriter), true )) {
               override protected def parentClassLoader = pane.getClass.getClassLoader
            }

            in.setContextClassLoader()
//            bindingsCreator.foreach( _.apply( in ))
            customBindings.foreach( in.bind( _ ))
            in.addImports( customImports: _* )

            initialCode.foreach( in.interpret( _ ))
            val cmp = new JLineCompletion( in )
            compVar = Some( cmp )
//            interpreterVar = Some( in )
         }

         override protected def done() {
            ggProgressInvis.setVisible( true )
            ggProgress.setVisible( false )
            editorPane.setContentType( "text/scala" )
            editorPane.setText( initialText )
            docVar = editorPane.getDocument match {
               case sdoc: SyntaxDocument => Some( sdoc )
               case _ => None
            }

            editorPane.setFont( createFont )
            editorPane.setEnabled( true )
            editorPane.requestFocus()
            status( "Ready." )
         }
      }).execute()

      val ggScroll   = new JScrollPane( editorPane, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_ALWAYS  )
//      ggScroll.putClientProperty( "JComponent.sizeVariant", "small" )

      ggProgress.putClientProperty( "JProgressBar.style", "circular" )
      ggProgress.setIndeterminate( true )
      ggProgressInvis.setVisible( false )
      editorPane.setEnabled( false )

      val imap = editorPane.getInputMap( JComponent.WHEN_FOCUSED )
      val amap = editorPane.getActionMap
      imap.put( executeKeyStroke, "de.sciss.exec" )
      amap.put( "de.sciss.exec", new AbstractAction {
         def actionPerformed( e: ActionEvent ) {
            getSelectedTextOrCurrentLine.foreach( interpret( _ ))
         }
      })
      imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_SPACE, InputEvent.CTRL_MASK ), "de.sciss.comp" )
      amap.put( "de.sciss.comp", new CompletionAction( compVar.map( _.completer() )))

      customKeyMapActions.iterator.zipWithIndex.foreach( tup => {
         val (spec, idx) = tup
         val name = "de.sciss.user" + idx
         imap.put( spec._1, name )
         amap.put( name, new AbstractAction {
            def actionPerformed( e: ActionEvent ) {
               spec._2.apply()
            }
         })
      })

      progressPane.setLayout( new OverlayLayout( progressPane ))
      progressPane.add( ggProgress )
      progressPane.add( ggProgressInvis )
      ggStatus.putClientProperty( "JComponent.sizeVariant", "small" )
      val statusPane = Box.createHorizontalBox()
      statusPane.add( Box.createHorizontalStrut( 4 ))
      statusPane.add( progressPane )
      statusPane.add( Box.createHorizontalStrut( 4 ))
      statusPane.add( ggStatus )

//      setLayout( new BorderLayout )
      setLayout( new BorderLayout() )
      add( ggScroll, BorderLayout.CENTER )
      add( statusPane, BorderLayout.SOUTH )
   }

   def getSelectedText : Option[ String ] = {
      val txt = editorPane.getSelectedText
      if( txt != null ) Some( txt ) else None
   }

   def getCurrentLine : Option[ String ] =
      docVar.map( _.getLineAt( editorPane.getCaretPosition ))

   def getSelectedTextOrCurrentLine : Option[ String ] =
      getSelectedText.orElse( getCurrentLine )

   protected def status( s: String ) {
      ggStatus.setText( s )
   }

   def interpret( code: String ) {
      interpreter.foreach { in =>
         status( null )
         try { in.interpret( code ) match {
            case Results.Error       => status( "! Error !" )
            case Results.Success     => status( "Ok. <" + in.mostRecentVar + ">" )
            case Results.Incomplete  => status( "! Code incomplete !" )
            case _ =>
         }}
         catch { case e => e.printStackTrace() }
      }
   }
}
