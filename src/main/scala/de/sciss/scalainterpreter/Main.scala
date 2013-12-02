/*
 *  Main.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2013 Hanns Holger Rutz. All rights reserved.
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
import javax.swing.{JFrame, WindowConstants}
import scala.util.control.NonFatal

/** The standalone application object */
object Main extends App with Runnable {
  EventQueue.invokeLater(this)

  def run(): Unit = {
    //      javax.swing.UIManager.setLookAndFeel( "javax.swing.plaf.metal.MetalLookAndFeel" )

    val pCfg  = InterpreterPane.Config()
    val bi    = Class.forName("de.sciss.scalainterpreter.BuildInfo")
    try {
      val name    = bi.getMethod("name").invoke(null)
      val version = bi.getMethod("version").invoke(null)
      pCfg.code   = "println(\"Welcome to " + name + " v" + version + "\")"
    } catch {
      case NonFatal(_) =>
    }
    //      pCfg.prependExecutionInfo = false
    //      pCfg.executeKey = javax.swing.KeyStroke.getKeyStroke( java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.META_MASK )

    val iCfg = Interpreter.Config()
    //      iCfg.bindings :+= NamedParam( "gaga", 33 )
    //      iCfg.imports = iCfg.imports :+ "javax.swing._"

    val cCfg = CodePane.Config()
    //      cCfg.font = Seq( "Helvetica" -> 16 )
    //      cCfg.keyProcessor = { k: java.awt.event.KeyEvent => println( "Pling" ); k }
    //      cCfg.text = "math.Pi"
    //      cCfg.style = Style.Light
    //      cCfg.keyMap += javax.swing.KeyStroke.getKeyStroke( java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.META_MASK ) ->
    //         { () => println( "Tschuschu" )}

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