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

import java.awt.GraphicsEnvironment
import javax.swing.UIManager
import scala.swing.{Swing, MainFrame, Frame, SimpleSwingApplication}
import scala.util.control.NonFatal
import Swing._

/** The standalone application object */
object Main extends SimpleSwingApplication {
  lazy val top: Frame = {
    // val lafName = "javax.swing.plaf.metal.MetalLookAndFeel"
    // val lafName = "javax.swing.plaf.nimbus.NimbusLookAndFeel"
    val lafName = UIManager.getSystemLookAndFeelClassName
    UIManager.setLookAndFeel(lafName)

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
    val b       = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
    split.component.dividerLocation = b.height * 2 / 3
    new MainFrame {
      title = "Scala Interpreter"
      contents = split.component
      size = (b.width / 2, b.height * 5 / 6)
      centerOnScreen()
      open()
    }
  }
}