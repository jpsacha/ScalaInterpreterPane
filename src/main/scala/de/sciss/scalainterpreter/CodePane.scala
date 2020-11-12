/*
 *  CodePane.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.scalainterpreter

import java.awt.event.KeyEvent

import de.sciss.scalainterpreter.impl.{CodePaneImpl => Impl}
import de.sciss.syntaxpane.Token
import javax.swing.KeyStroke

import scala.collection.immutable.{Seq => ISeq}
import scala.language.implicitConversions
import scala.swing.{Action, Component, EditorPane}

object CodePane {
  object Config {
    implicit def build(b: ConfigBuilder): Config = b.build

    def apply(): ConfigBuilder = Impl.newConfigBuilder()
  }

  sealed trait ConfigLike {
    /** The initial text to be shown in the pane */
    def text: String

    /** The color scheme to use */
    def style: Style

    /** A map of custom keyboard action bindings */
    def keyMap: Map[KeyStroke, () => Unit]

    /** A pre-processor function for key events */
    def keyProcessor: KeyEvent => KeyEvent

    /** A list of preferred font faces, given as pairs of font name and font size.
      * The code pane tries to find the first matching font, therefore put the
      * preferred faces in the beginning of the sequence, and the fall-back faces
      * in the end.
      */
    def font: ISeq[(String, Int)]

    /** Preferred width and height of the component */
    def preferredSize: (Int, Int)

    //      def toBuilder : ConfigBuilder
  }

  trait Config extends ConfigLike

  object ConfigBuilder {
    def apply(config: Config): ConfigBuilder = Impl.mkConfigBuilder(config)
  }

  trait ConfigBuilder extends ConfigLike {
    var text          : String
    var style         : Style
    var keyMap        : Map[KeyStroke, () => Unit]
    var keyProcessor  : KeyEvent => KeyEvent
    var font          : ISeq[(String, Int)]
    var preferredSize : (Int, Int)
    def build         : Config
  }

  def apply(config: Config = Config().build): CodePane = Impl(config)

  final case class Range(start: Int, stop: Int, selected: Boolean) {
    def length: Int = stop - start
  }
}

trait CodePane {
  import CodePane.Range

  /** The peer swing component which can be added to the parent swing container. */
  def component: Component

  def editor: EditorPane

  /** The currently selected text, or `None` if no selection has been made. */
  def selectedText: Option[String]

  /** The text on the current line, or `None` if the document is empty or unavailable. */
  def currentTextLine: Option[String]

  /** Convenience method for `getSelectedText orElse getCurrentTextLine`. */
  def activeText: Option[String]

  def selectedRange: Option[Range]

  def currentLineRange: Option[Range]

  def activeRange: Option[Range]

  def getTextSlice(range: Range): String

  def flash(range: Range): Unit

  def abortFlash(): Unit

  def activeToken: Option[Token]

  def installAutoCompletion(interpreter: Interpreter): Unit

  def undoAction: Action
  def redoAction: Action

  def clearUndoHistory(): Unit
}