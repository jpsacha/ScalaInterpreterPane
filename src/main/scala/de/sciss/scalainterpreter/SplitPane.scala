/*
 *  SplitPane.scala
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

import javax.swing.{SwingConstants, JSplitPane}

object SplitPane {
   def apply( paneSettings: InterpreterPane.Settings = InterpreterPane.Settings().build,
              interpreterSettings: Interpreter.Settings = Interpreter.Settings().build,
              codePaneSettings: CodePane.Settings = CodePane.Settings().build ) : SplitPane = {
      val lp   = LogPane().makeDefault()
      val intSet = interpreterSettings.toBuilder
      intSet.out = Some( lp.writer )
      val ip = InterpreterPane( paneSettings, intSet.build, codePaneSettings )
      val sp = new JSplitPane( SwingConstants.HORIZONTAL )
      sp.setTopComponent( ip.component )
      sp.setBottomComponent( lp.component )
      new Impl( sp, ip )
   }

   private final class Impl( val component: JSplitPane, val interpreter: InterpreterPane )
   extends SplitPane {
      override def toString = "SplitPane@" + hashCode.toHexString
   }
}
sealed trait SplitPane {
   def component: JSplitPane
   def interpreter: InterpreterPane
}