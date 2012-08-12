package de.sciss.scalainterpreter.actions

import java.awt.event.ActionEvent
import jsyntaxpane.SyntaxDocument
import javax.swing.text.JTextComponent
import jsyntaxpane.actions.gui.ComboCompletionDialog
import jsyntaxpane.actions.DefaultSyntaxAction
import tools.nsc.interpreter.Completion.ScalaCompleter
import collection.JavaConversions

class CompletionAction( completer: ScalaCompleter ) extends DefaultSyntaxAction( "COMPLETION" ) {
   private var dlg: ComboCompletionDialog = null

   override def actionPerformed( target: JTextComponent, sdoc: SyntaxDocument, dot: Int, e: ActionEvent ) {
      val (cw, start) = {
         val sel = target.getSelectedText
         if( sel != null ) {
            (sel, target.getSelectionStart)
         } else {
            val line    = sdoc.getLineAt( dot )
            val start   = sdoc.getLineStartOffset( dot )
//               val stop    = sdoc.getLineEndOffset( dot )
            (line.substring( 0, dot - start ), start)
         }
      }

//      target.select( current.start, current.end() )

      val cwlen = cw.length()
      val m = completer.complete( cw, cwlen )
      if( m.candidates.isEmpty ) return

      val off = start + m.cursor
      target.select( off, start + cwlen )
//println( "select(" + off + ", " + (start + cwlen) + ") -- cw = '" + cw + "'" )
      m.candidates match {
         case one :: Nil =>
            target.replaceSelection( one )
         case more =>
            if( dlg == null ) {
                dlg = new ComboCompletionDialog( target )
            }
//println( "more = " + more )
//               val arr = new ArrayList[ String ]
//               more.foreach( arr.add( _ ))
//               dlg.displayFor( cw.substring( m.cursor ), arr )
            dlg.displayFor( cw.substring( m.cursor ), JavaConversions.seqAsJavaList( more ))
      }
   }
}