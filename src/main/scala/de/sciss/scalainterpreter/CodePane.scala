/*
 *  CodePane.scala
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
import javax.swing.{AbstractAction, JEditorPane, KeyStroke, JComponent}
import java.awt.event.{InputEvent, ActionEvent, KeyEvent}
import jsyntaxpane.{DefaultSyntaxKit, SyntaxDocument}
import java.awt.Color
import jsyntaxpane.util.Configuration

object CodePane {
   object Settings {
      implicit def fromBuilder( b: SettingsBuilder ) : Settings = b.build
      def apply() : SettingsBuilder = new SettingsBuilderImpl
   }
   sealed trait Settings {
      /**
       * The initial text to be shown in the pane
       */
      def text: String

      /**
       * The color scheme to use
       */
      def style: Style

      /**
       * A map of custom keyboard action bindings
       */
      def keyMap: Map[ KeyStroke, () => Unit ]

      /**
       * A pre-processor function for key events
       */
      def keyProcessor: KeyEvent => KeyEvent

      /**
       * A list of preferred font faces, given as pairs of font name and font size.
       * The code pane tries to find the first matching font, therefore put the
       * preferred faces in the beginning of the sequence, and the fall-back faces
       * in the end.
       */
      def font: Seq[ (String, Int) ]
   }
   sealed trait SettingsBuilder extends Settings {
      def text_=( value: String ) : Unit
      def style_=( value: Style ) : Unit
      def keyMap_=( value: Map[ KeyStroke, () => Unit ]) : Unit
      def keyProcessor_=( value: KeyEvent => KeyEvent ) : Unit
      def font_=( value: Seq[ (String, Int) ]) : Unit
      def build : Settings
   }

   private final class SettingsBuilderImpl extends SettingsBuilder {
      var text = ""
      var style: Style = Style.BlueForest
      var keyMap = Map.empty[ KeyStroke, () => Unit ]
      var keyProcessor: KeyEvent => KeyEvent = identity
      var font = Seq[ (String, Int) ](
         "Menlo" -> 12,
         "DejaVu Sans Mono" -> 12,
         "Bitstream Vera Sans Mono" -> 12,
         "Monaco" -> 12,
         "Anonymous Pro" -> 12
      )

      def build: Settings = SettingsImpl( text, keyMap, keyProcessor, font, style )
      override def toString = "CodePane.SettingsBuilder@" + hashCode().toHexString
   }

   private final case class SettingsImpl( text: String, keyMap: Map[ KeyStroke, () => Unit ],
                                          keyProcessor: KeyEvent => KeyEvent, font: Seq[ (String, Int) ],
                                          style: Style )
   extends Settings {
      override def toString = "CodePane.Settings@" + hashCode().toHexString
   }

   private def put( cfg: Configuration, key: String, pair: (Color, Style.Face) ) {
      val value = "0x" + (pair._1.getRGB | 0xFF000000).toHexString.substring( 2 ) + ", " + pair._2.code
      cfg.put( key, value )
   }

   private def put( cfg: Configuration, key: String, color: Color ) {
      val value = "0x" + (color.getRGB | 0xFF000000).toHexString.substring( 2 )
      cfg.put( key, value )
   }

   def initKit( settings: Settings ) {
      DefaultSyntaxKit.initKit()
      DefaultSyntaxKit.registerContentType( "text/scala", "de.sciss.scalainterpreter.ScalaSyntaxKit" )
      val syn = DefaultSyntaxKit.getConfig( classOf[ ScalaSyntaxKit ])
      val style = settings.style
      put( syn, "Style.DEFAULT",    style.default    )
      put( syn, "Style.KEYWORD",    style.keyword    )
      put( syn, "Style.OPERATOR",   style.operator   )
      put( syn, "Style.COMMENT",    style.comment    )
      put( syn, "Style.NUMBER",     style.number     )
      put( syn, "Style.STRING",     style.string     )
      put( syn, "Style.STRING2",    style.string     )
      put( syn, "Style.IDENTIFIER", style.identifier )
      put( syn, "Style.DELIMITER",  style.delimiter  )
      put( syn, "Style.TYPE",       style.tpe        )

      put( syn, "LineNumbers.CurrentBack", style.lineBackground )
      put( syn, "LineNumbers.Foreground",  style.lineForeground )
      put( syn, "SelectionColor",          style.selection )
      put( syn, "CaretColor",              style.caret )
      put( syn, "PairMarker.Color",        style.pair )

      syn.put( "SingleColorSelect", style.singleColorSelect.toString )
   }

   def apply( settings: Settings = Settings().build ) : CodePane = {
      initKit( settings )
      val res = createPlain( settings )
      res.init()
      res
   }

   private[scalainterpreter] def createPlain( settings: Settings ) : CodePane = {
      val ed: JEditorPane = new JEditorPane() {
         override protected def processKeyEvent( e: KeyEvent ) {
            super.processKeyEvent( settings.keyProcessor( e ))
         }
      }
      ed.setBackground( new Color( 0x14, 0x1F, 0x2E ))  // stupid... this cannot be set in the kit config
      ed.setForeground( new Color( 0xF5, 0xF5, 0xF5 ))
      ed.setSelectedTextColor( new Color( 0xF5, 0xF5, 0xF5 ))

      val imap = ed.getInputMap( JComponent.WHEN_FOCUSED )
      val amap = ed.getActionMap
//      imap.put( executeKeyStroke, "de.sciss.exec" )
//      amap.put( "de.sciss.exec", new AbstractAction {
//         def actionPerformed( e: ActionEvent ) {
//            getSelectedTextOrCurrentLine.foreach( interpret( _ ))
//         }
//      })
//      imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_SPACE, InputEvent.CTRL_MASK ), "de.sciss.comp" )
//      amap.put( "de.sciss.comp", new CompletionAction( compVar.map( _.completer() )))

      settings.keyMap.iterator.zipWithIndex.foreach { case (spec, idx) =>
         val name = "de.sciss.user" + idx
         imap.put( spec._1, name )
         amap.put( name, new AbstractAction {
            def actionPerformed( e: ActionEvent ) {
               spec._2.apply()
            }
         })
      }

      new Impl( ed, settings )
   }

   private final class Impl( val component: JEditorPane, settings: Settings ) extends CodePane {
      def docOption: Option[ SyntaxDocument ] = sys.error( "TODO" )

      def init() {
         component.setContentType( "text/scala" )
         component.setText( settings.text )
         component.setFont( aux.Helper.createFont( settings.font ))
      }

      def getSelectedText : Option[ String ] = {
         val txt = component.getSelectedText
         if( txt != null ) Some( txt ) else None
      }

      def getCurrentLine : Option[ String ] =
         docOption.map( _.getLineAt( component.getCaretPosition ))

      def getSelectedTextOrCurrentLine : Option[ String ] =
         getSelectedText.orElse( getCurrentLine )

      def installAutoCompletion( interpreter: Interpreter ) {
         val imap = component.getInputMap( JComponent.WHEN_FOCUSED )
         val amap = component.getActionMap
         imap.put( KeyStroke.getKeyStroke( KeyEvent.VK_SPACE, InputEvent.CTRL_MASK ), "de.sciss.comp" )
         amap.put( "de.sciss.comp", new CompletionAction( interpreter.completer ))
      }

//      def installExecutionAction( interpreter: Interpreter, key: KeyStroke ) {
//         val imap = ed.getInputMap( JComponent.WHEN_FOCUSED )
//         val amap = ed.getActionMap
//         imap.put( executeKeyStroke, "de.sciss.exec" )
//         amap.put( "de.sciss.exec", new AbstractAction {
//            def actionPerformed( e: ActionEvent ) {
//               getSelectedTextOrCurrentLine.foreach( interpret( _ ))
//            }
//         })
//      }

      override def toString = "CodePane@" + hashCode().toHexString
   }
}
trait CodePane {
   /**
    * The peer swing component which can be added to the parent swing container.
    */
   def component: JComponent

   /**
    * The currently selected text, or `None` if no selection has been made.
    */
   def getSelectedText : Option[ String ]

   /**
    * The text on the current line, or `None` if the document is empty or unavailable.
    */
   def getCurrentLine : Option[ String ]

   /**
    * Convenience method for `getSelectedText orElse getCurrentLine`.
    */
   def getSelectedTextOrCurrentLine : Option[ String ]

   def installAutoCompletion( interpreter: Interpreter ) : Unit
//   def installExecutionAction( interpreter: Interpreter, key: KeyStroke ) : Unit

   private[scalainterpreter] def init() : Unit
}