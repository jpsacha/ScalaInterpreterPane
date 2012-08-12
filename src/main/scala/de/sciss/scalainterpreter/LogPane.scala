/*
 *  LogPane.scala
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
 *	  Below is a copy of the GNU Lesser General Public License
 *
 *	  For further information, please contact Hanns Holger Rutz at
 *	  contact@sciss.de
 */

package de.sciss.scalainterpreter

import java.awt.{ BorderLayout, Color }
import java.io.{ IOException, OutputStream, Writer }
import java.awt.event.{ActionEvent, MouseEvent, MouseAdapter}
import javax.swing.{JComponent, JPopupMenu, AbstractAction, JPanel, JScrollPane, JTextArea, ScrollPaneConstants}
import ScrollPaneConstants._

object LogPane {
   object Settings {
      implicit def fromBuilder( b: SettingsBuilder ) : Settings = b.build
      def apply() : SettingsBuilder = new SettingsBuilderImpl
   }
   sealed trait Settings {
      def rows: Int
      def columns: Int
      def style: Style
      def font: Seq[ (String, Int) ]
   }
   sealed trait SettingsBuilder extends Settings {
      def rows_=( value: Int ) : Unit
      def columns_=( value: Int ) : Unit
      def style_=( value: Style ) : Unit
      def font_=( value: Seq[ (String, Int) ]) : Unit
      def build : Settings
   }
   private final class SettingsBuilderImpl extends SettingsBuilder {
      var rows    = 10
      var columns = 60
      var style : Style = Style.BlueForest
      var font = aux.Helper.defaultFonts
      def build : Settings = SettingsImpl( rows, columns, style, font )
      override def toString = "LogPane.SettingsBuilder@" + hashCode.toHexString
   }
   private final case class SettingsImpl( rows: Int, columns: Int, style: Style, font: Seq[ (String, Int) ])
   extends Settings {
      override def toString = "LogPane.Settings@" + hashCode.toHexString
   }

   def apply( settings: Settings = Settings().build ) : LogPane = new Impl( settings )

   private final class Impl( settings: Settings ) extends LogPane {
      pane =>

      override def toString = "LogPane@" + hashCode.toHexString

      private val textPane: JTextArea = new JTextArea( settings.rows, settings.columns ) {
         me =>

         private var totalLength = 0

         setFont( aux.Helper.createFont( settings.font ))
         setEditable( false )
         setLineWrap( true )
         setBackground( settings.style.background ) // Color.black )
         setForeground( settings.style.foreground ) // Color.white )
         addMouseListener( new MouseAdapter {
            override def mousePressed( e: MouseEvent ) { handleButton( e )}
            override def mouseReleased( e: MouseEvent ) { handleButton( e )}

            private def handleButton( e: MouseEvent ) {
               if( e.isPopupTrigger ) {
   //               textPane.add( popup )
                  popup.show( me, e.getX, e.getY )
               }
            }
         })

         override def append( str: String ) {
            super.append( str );
            totalLength += str.length
            updateCaret()
         }

         override def setText( str: String ) {
            super.setText( str )
            totalLength = if( str == null ) 0 else str.length
         }

         private def updateCaret() {
            try {
               setCaretPosition( math.max( 0, totalLength - 1 ))
            }
            catch { case _: Throwable => /* ignore */ }
         }
      }

      // ---- Writer ----
      val writer: Writer = new Writer {
         override def toString = pane.toString + ".writer"

         def close() {}
         def flush() {}

         def write( ch: Array[ Char ], off: Int, len: Int ) {
            val str = new String( ch, off, len );
            textPane.append( str )
         }
      }

      // ---- OutputStream ----
      val outputStream : OutputStream = new OutputStream {
         override def toString = pane.toString + ".outputStream"

         override def write( b: Array[ Byte ], off: Int, len: Int ) {
            val str = new String( b, off, len )
            textPane.append( str )
         }

         def write( b: Int ) {
            write( Array( b.toByte ), 0, 1 )
         }
      }

      val component = new JScrollPane( textPane, VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_NEVER )

      private val popup = {
         val p = new JPopupMenu()
         p.add( new AbstractAction( "Clear All" ) {
            override def actionPerformed( e: ActionEvent ) {
               clear()
            }
         })
         p
      }

      def clear() {
         textPane.setText( null )
      }

      def makeDefault() : LogPane = {
         Console.setOut( outputStream )
         Console.setErr( outputStream )
         this
      }
   }
}
trait LogPane {
   def component: JComponent
   def writer : Writer
   def outputStream : OutputStream
   def clear() : Unit
   def makeDefault() : LogPane
}