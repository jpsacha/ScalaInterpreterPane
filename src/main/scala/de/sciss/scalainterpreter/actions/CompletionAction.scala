/*
 *  CompletionAction.scala
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
package actions

import java.awt.event.ActionEvent
import jsyntaxpane.SyntaxDocument
import javax.swing.text.JTextComponent
import jsyntaxpane.actions.gui.ComboCompletionDialog
import jsyntaxpane.actions.DefaultSyntaxAction
import tools.nsc.interpreter.Completion.ScalaCompleter
import collection.JavaConversions

class CompletionAction(completer: ScalaCompleter) extends DefaultSyntaxAction("COMPLETION") {
  private var dlg: ComboCompletionDialog = null

  override def actionPerformed(target: JTextComponent, sdoc: SyntaxDocument, dot: Int, e: ActionEvent): Unit = {
    val (cw, start) = {
      val sel = target.getSelectedText
      if (sel != null) {
        (sel, target.getSelectionStart)
      } else {
        val line  = sdoc.getLineAt(dot)
        val start = sdoc.getLineStartOffset(dot)
        // val stop = sdoc.getLineEndOffset( dot )
        (line.substring(0, dot - start), start)
      }
    }

    //      target.select( current.start, current.end() )

    val cwlen = cw.length()
    val m     = completer.complete(cw, cwlen)
    if (m.candidates.isEmpty) return

    val off = start + m.cursor
    target.select(off, start + cwlen)
    //println( "select(" + off + ", " + (start + cwlen) + ") -- cw = '" + cw + "'" )
    m.candidates match {
      case one :: Nil =>
        target.replaceSelection(one)
      case more =>
        if (dlg == null) {
          dlg = new ComboCompletionDialog(target)
        }
        //println( "more = " + more )
        //               val arr = new ArrayList[ String ]
        //               more.foreach( arr.add( _ ))
        //               dlg.displayFor( cw.substring( m.cursor ), arr )
        dlg.displayFor(cw.substring(m.cursor), JavaConversions.seqAsJavaList(more))
    }
  }
}