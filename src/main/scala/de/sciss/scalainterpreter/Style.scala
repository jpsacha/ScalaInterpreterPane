/*
 *  Style.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.scalainterpreter

import java.awt.Color

object Style {
  private def c(hex: Int): Color = new Color(hex)

  /** A simple light color scheme. */
  object Light extends Style {

    import Face._

    val default       : Pair  = c(0x000000) -> Plain
    val keyword       : Pair  = c(0x3333ee) -> Plain
    val keyword2      : Pair  = c(0x9133ee) -> BoldItalic
    val operator      : Pair  = c(0x000000) -> Plain
    val comment       : Pair  = c(0x338855) -> Italic // c(0x339933) -> Italic
    val number        : Pair  = c(0x999933) -> Bold
    val string        : Pair  = c(0x674d00 /* 0xcc6600 */) -> Plain
    val identifier    : Pair  = c(0xe60035 /* 0xff003b */) -> Plain
    val tpe           : Pair  = c(0x000000) -> Italic
    val tpeStd        : Pair  = c(0x000000) -> Bold
    val tpeUser       : Pair  = c(0x000000) -> Italic
    val delimiter     : Pair  = c(0xe60035 /* 0xff003b */) -> Plain

    val background    : Color = c(0xffffff)
    val foreground    : Color = c(0x000000)
    val lineBackground: Color = c(0xb0b0d0) // c(0x1b2b40)
    val lineForeground: Color = c(0x606060) // c(0x808080)
    val selection     : Color = c(0xc0c0d0) // c(0x375780)
    val caret         : Color = c(0x000000)
    val pair          : Color = c(0xa0a0a0) // c(0x3c5f8c)

    val singleColorSelect = false
  }

  object Face {
    case object Plain       extends Face { val code = 0 }
    case object Bold        extends Face { val code = 1 }
    case object Italic      extends Face { val code = 2 }
    case object BoldItalic  extends Face { val code = 3 }
  }
  sealed trait Face { def code: Int }

  type Pair = (Color, Face)

  /** A dark color scheme with focus on blue and yellow/orange, developed by Mathias Doenitz (sirthias).
    * The original color table can be found at https://github.com/sirthias/BlueForest
    */
  object BlueForest extends Style {

    import Face._

    val default       : Pair  = c(0xf5f5f5) -> Plain
    val keyword       : Pair  = c(0x0099ff) -> Bold
    val keyword2      : Pair  = c(0x90ff90) -> BoldItalic
    val operator      : Pair  = c(0xf5f5f5) -> Plain
    val comment       : Pair  = c(0x50f050) -> Italic
    val number        : Pair  = c(0xff8080) -> Plain
    val string        : Pair  = c(0xa0ffa0) -> Plain
    val identifier    : Pair  = c(0xff194f /* 0xff003b */) -> Plain
    val tpe           : Pair  = c(0x91ccff) -> Plain
    val tpeStd        : Pair  = c(0xf5f5f5) -> Bold
    val tpeUser       : Pair  = c(0xf5f5f5) -> Italic
    val delimiter     : Pair  = c(0xff194f /* 0xff003b */) -> Plain

    val background    : Color = c(0x141f2e)
    val foreground    : Color = c(0xf5f5f5)
    val lineBackground: Color = c(0x1b2b40)
    val lineForeground: Color = c(0xA0A0A0)
    val selection     : Color = c(0x375780)
    val caret         : Color = c(0xffffff)
    val pair          : Color = c(0x3c5f8c)

    val singleColorSelect = true
  }
}

trait Style {
  import Style.Pair

  def default           : Pair
  def keyword           : Pair
  def keyword2          : Pair
  def operator          : Pair
  def comment           : Pair
  def number            : Pair
  def string            : Pair
  def identifier        : Pair
  def tpe               : Pair
  def tpeStd            : Pair
  def tpeUser           : Pair
  def delimiter         : Pair
  def background        : Color
  def foreground        : Color
  def selection         : Color
  def caret             : Color
  def pair              : Color
  def lineBackground    : Color
  def lineForeground    : Color
  def singleColorSelect : Boolean
}
