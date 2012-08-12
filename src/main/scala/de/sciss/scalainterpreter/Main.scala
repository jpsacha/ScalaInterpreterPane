/*
 *  Main.scala
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

import java.awt.{ EventQueue, GraphicsEnvironment }
import javax.swing.{ JFrame, JSplitPane, SwingConstants, WindowConstants }

object Main extends App with Runnable {
   EventQueue.invokeLater( this )

   def run() {
      val split   = SplitPane()
      val frame = new JFrame( "Scala Interpreter" )
      val cp = frame.getContentPane
      cp.add( split.component )
      val b = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
      frame.setSize( b.width / 2, b.height * 5 / 6 )
      split.component.setDividerLocation( b.height * 2 / 3 )
      frame.setLocationRelativeTo( null )
      frame.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
      frame.setVisible( true )
   }
}