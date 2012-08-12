package de.sciss.scalainterpreter

import java.awt.{ EventQueue, GraphicsEnvironment }
import javax.swing.{ JFrame, JSplitPane, SwingConstants, WindowConstants }

object Main extends App with Runnable {
   EventQueue.invokeLater( this )

   def run() {
      val ip = InterpreterPane()()
      val lp = new LogPane
      lp.init()
//      ip.out = Some( lp.writer )
      Console.setOut( lp.outputStream )
      Console.setErr( lp.outputStream )
//      ip.init()

      val frame = new JFrame( "Scala Interpreter" )
      val sp = new JSplitPane( SwingConstants.HORIZONTAL )
      sp.setTopComponent( ip.component )
      sp.setBottomComponent( lp )
      val cp = frame.getContentPane
      cp.add( sp )
      val b = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
      frame.setSize( b.width / 2, b.height * 5 / 6 )
      sp.setDividerLocation( b.height * 2 / 3 )
      frame.setLocationRelativeTo( null )
      frame.setDefaultCloseOperation( WindowConstants.EXIT_ON_CLOSE )
      frame.setVisible( true )
   }
}