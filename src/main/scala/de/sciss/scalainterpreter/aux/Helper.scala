package de.sciss.scalainterpreter
package aux

import java.awt.{Font, GraphicsEnvironment}

private[scalainterpreter] object Helper {
   def createFont( list: Seq[ (String, Int) ]) : Font = {
      val allFontNames           = GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames
      val (fontName, fontSize)   = list.find( spec => allFontNames.contains( spec._1 ))
         .getOrElse( "Monospaced" -> 12 )

      new Font( fontName, Font.PLAIN, /*if( isMac )*/ fontSize /*else fontSize * 3/4*/ )
   }
}
