/*
 *  SplitPane.scala
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

import javax.swing.{SwingConstants, JSplitPane}

object SplitPane {
  def apply(paneConfig: InterpreterPane.Config = InterpreterPane.Config().build,
            interpreterConfig: Interpreter.Config = Interpreter.Config().build,
            codePaneConfig: CodePane.Config = CodePane.Config().build): SplitPane = {
    val lp      = LogPane().makeDefault()
    val intCfg  = Interpreter.ConfigBuilder(interpreterConfig)
    intCfg.out  = Some(lp.writer)
    val ip      = InterpreterPane(paneConfig, intCfg.build, codePaneConfig)
    val sp      = new JSplitPane(SwingConstants.HORIZONTAL)
    sp.setTopComponent   (ip.component)
    sp.setBottomComponent(lp.component)
    new Impl(sp, ip)
  }

  private final class Impl(val component: JSplitPane, val interpreter: InterpreterPane)
    extends SplitPane {

    override def toString = s"SplitPane@${hashCode.toHexString}"
  }
}

trait SplitPane {
  def component: JSplitPane

  def interpreter: InterpreterPane
}