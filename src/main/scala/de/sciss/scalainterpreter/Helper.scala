/*
 *  Helper.scala
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

import java.awt.{Font, GraphicsEnvironment}
import collection.immutable.{Seq => ISeq}

private[scalainterpreter] object Helper {
  val defaultFonts = ISeq[(String, Int)](
    "Menlo"                     -> 12,
    "DejaVu Sans Mono"          -> 12,
    "Bitstream Vera Sans Mono"  -> 12,
    "Monaco"                    -> 12,
    "Anonymous Pro"             -> 12
  )

  def createFont(list: ISeq[(String, Int)]): Font = {
    val allFontNames = GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames
    val (fontName, fontSize) = list.find(spec => allFontNames.contains(spec._1))
      .getOrElse("Monospaced" -> 12)

    new Font(fontName, Font.PLAIN, /*if( isMac )*/ fontSize /*else fontSize * 3/4*/)
  }
}
