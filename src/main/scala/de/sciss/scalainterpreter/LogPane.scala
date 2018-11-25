/*
 *  LogPane.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *	  Below is a copy of the GNU Lesser General Public License
 *
 *	  For further information, please contact Hanns Holger Rutz at
 *	  contact@sciss.de
 */

package de.sciss.scalainterpreter

import java.awt.event.ActionEvent
import java.io.{OutputStream, PrintStream, Writer}
import javax.swing.{AbstractAction, JPopupMenu, JTextArea}

import scala.collection.immutable.{Seq => ISeq}
import scala.language.implicitConversions
import scala.swing.event.{MouseButtonEvent, MousePressed, MouseReleased}
import scala.swing.{Component, ScrollPane, TextArea}
import scala.util.control.NonFatal

object LogPane {
  object Config {
    implicit def fromBuilder(b: ConfigBuilder): Config = b.build

    def apply(): ConfigBuilder = new ConfigBuilderImpl
  }

  trait Config {
    def rows    : Int
    def columns : Int
    def style   : Style
    def font    : Fonts.List
  }

  trait ConfigBuilder extends Config {
    var rows    : Int
    var columns : Int
    var style   : Style
    var font    : Fonts.List
    def build   : Config
  }

  private final class ConfigBuilderImpl extends ConfigBuilder {
    var rows   : Int          = 10
    var columns: Int          = 60
    var style  : Style        = Style.BlueForest
    var font   : Fonts.List = Fonts.defaultFonts

    def build: Config = ConfigImpl(rows, columns, style, font)

    override def toString = s"LogPane.ConfigBuilder@${hashCode.toHexString}"
  }

  private final case class ConfigImpl(rows: Int, columns: Int, style: Style, font: ISeq[(String, Int)])
    extends Config {

    override def toString = s"LogPane.Config@${hashCode.toHexString}"
  }

  def apply(config: Config = Config().build): LogPane = new Impl(config)

  private final class Impl(config: Config) extends LogPane {
    pane =>

    override def toString = s"LogPane@${hashCode.toHexString}"

    private val textPane: TextArea = new TextArea(config.rows, config.columns) {
      override lazy val peer: JTextArea = new JTextArea(config.rows, config.columns) with SuperMixin {
        override def append(str: String): Unit = {
          super.append(str)
          totalLength += str.length
          updateCaret()
        }

        override def setText(str: String): Unit = {
          super.setText(str)
          totalLength = if (str == null) 0 else str.length
        }
      }

      private var totalLength = 0

      font      = Fonts.create(config.font)
      editable  = false
      lineWrap  = true

      background = config.style.background
      foreground = config.style.foreground

      listenTo(mouse.clicks)
      reactions += {
        case e: MousePressed  => handleButton(e)
        case e: MouseReleased => handleButton(e)
      }

      private def handleButton(e: MouseButtonEvent): Unit =
        if (e.triggersPopup) popup.show(peer, e.point.x, e.point.y)

      private def updateCaret(): Unit =
        try {
          caret.position = math.max(0, totalLength - 1)
        }
        catch {
          case NonFatal(_) => /* ignore */
        }
    }

    // ---- Writer ----
    val writer: Writer = new Writer {
      override def toString = s"$pane.writer"

      def close(): Unit = ()

      def flush(): Unit = ()

      def write(ch: Array[Char], off: Int, len: Int): Unit = {
        val str = new String(ch, off, len)
        textPane.append(str)
      }
    }

    // ---- OutputStream ----
    val outputStream: OutputStream = new OutputStream {
      override def toString = s"$pane.outputStream"

      override def write(b: Array[Byte], off: Int, len: Int): Unit = {
        val str = new String(b, off, len)
        textPane.append(str)
      }

      def write(b: Int): Unit = write(Array(b.toByte), 0, 1)
    }

    private val printStream = new PrintStream(outputStream, true)

    val component = new ScrollPane(textPane)
    component.peer.putClientProperty("styleId", "undecorated")
    component.verticalScrollBarPolicy = ScrollPane.BarPolicy.Always

    private val popup = {
      val p = new JPopupMenu()
      p.add(new AbstractAction("Clear All") {
        override def actionPerformed(e: ActionEvent): Unit = clear()
      })
      p
    }

    def clear(): Unit = textPane.text = null

    def makeDefault(error: Boolean): this.type = {
      // Console.setOut(outputStream)
      System.setOut(printStream)
      // if (error) Console.setErr(outputStream)
      if (error) System.setErr(printStream)
      this
    }
  }
}

/** A pane widget which can be used to log text output, and which can be hooked up to capture the
  * default console output.
  */
trait LogPane {
  /** The Swing component which can be added to a Swing parent container. */
  def component: Component

  /** A `Writer` which will write to the pane. */
  def writer: Writer

  /** An `OutputStream` which will write to the pane. */
  def outputStream: OutputStream

  /**  Clears the contents of the pane. */
  def clear(): Unit

  /** Makes this log pane the default text output for
    * `Console.out` and optionally for `Console.err` as well.
    *
    * @return  the method returns the log pane itself for convenience and method concatenation
    */
  def makeDefault(error: Boolean = true): this.type
}
