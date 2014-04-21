/*
 *  Main.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.scalainterpreter

import java.awt.{EventQueue, GraphicsEnvironment}
import javax.swing.{JFrame, WindowConstants}
import scala.util.control.NonFatal

/** The standalone application object */
object Main extends App with Runnable {
  EventQueue.invokeLater(this)

  def run(): Unit = {
    // javax.swing.UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel")
    javax.swing.UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel")

    val pCfg  = InterpreterPane.Config()
    val bi    = Class.forName("de.sciss.scalainterpreter.BuildInfo")
    try {
      val name    = bi.getMethod("name"   ).invoke(null)
      val version = bi.getMethod("version").invoke(null)
      pCfg.code   = s"""println("Welcome to $name v$version")"""
    } catch {
      case NonFatal(_) =>
    }

    val iCfg    = Interpreter.Config()
    val cCfg    = CodePane   .Config()

    val split   = SplitPane(paneConfig = pCfg, interpreterConfig = iCfg, codePaneConfig = cCfg)
    val frame   = new JFrame("Scala Interpreter")
    val cp      = frame.getContentPane
    val b       = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
    cp.add(split.component)
    frame.setSize(b.width / 2, b.height * 5 / 6)
    split.component.setDividerLocation(b.height * 2 / 3)
    frame.setLocationRelativeTo(null)
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
    frame.setVisible(true)
  }
}
