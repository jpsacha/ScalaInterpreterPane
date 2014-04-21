/*
 *  LogPane.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *	  Below is a copy of the GNU Lesser General Public License
 *
 *	  For further information, please contact Hanns Holger Rutz at
 *	  contact@sciss.de
 */

package de.sciss.scalainterpreter

import java.io.{PrintStream, OutputStream, Writer}
import java.awt.event.{ActionEvent, MouseEvent, MouseAdapter}
import javax.swing.{JComponent, JPopupMenu, AbstractAction, JScrollPane, JTextArea, ScrollPaneConstants}
import collection.immutable.{Seq => ISeq}
import scala.util.control.NonFatal
import language.implicitConversions

object LogPane {
  object Config {
    implicit def fromBuilder(b: ConfigBuilder): Config = b.build

    def apply(): ConfigBuilder = new ConfigBuilderImpl
  }

  sealed trait Config {
    def rows    : Int
    def columns : Int
    def style   : Style
    def font    : ISeq[(String, Int)]
  }

  sealed trait ConfigBuilder extends Config {
    var rows    : Int
    var columns : Int
    var style   : Style
    var font    : ISeq[(String, Int)]
    def build   : Config
  }

  private final class ConfigBuilderImpl extends ConfigBuilder {
    var rows    = 10
    var columns = 60
    var style   = Style.BlueForest: Style
    var font    = Helper.defaultFonts

    def build: Config = ConfigImpl(rows, columns, style, font)

    override def toString = "LogPane.ConfigBuilder@" + hashCode.toHexString
  }

  private final case class ConfigImpl(rows: Int, columns: Int, style: Style, font: ISeq[(String, Int)])
    extends Config {

    override def toString = "LogPane.Config@" + hashCode.toHexString
  }

  def apply(config: Config = Config().build): LogPane = new Impl(config)

  private final class Impl(config: Config) extends LogPane {
    pane =>

    override def toString = "LogPane@" + hashCode.toHexString

    private val textPane: JTextArea = new JTextArea(config.rows, config.columns) {
      me =>

      private var totalLength = 0

      setFont(Helper.createFont(config.font))
      setEditable(false)
      setLineWrap(true)
      setBackground(config.style.background) // Color.black )
      setForeground(config.style.foreground) // Color.white )
      addMouseListener(new MouseAdapter {
        override def mousePressed (e: MouseEvent): Unit = handleButton(e)
        override def mouseReleased(e: MouseEvent): Unit = handleButton(e)

        private def handleButton(e: MouseEvent): Unit =
          if (e.isPopupTrigger) popup.show(me, e.getX, e.getY)
      })

      override def append(str: String): Unit = {
        super.append(str)
        totalLength += str.length
        updateCaret()
      }

      override def setText(str: String): Unit = {
        super.setText(str)
        totalLength = if (str == null) 0 else str.length
      }

      private def updateCaret(): Unit =
        try {
          setCaretPosition(math.max(0, totalLength - 1))
        }
        catch {
          case NonFatal(_) => /* ignore */
        }
    }

    // ---- Writer ----
    val writer: Writer = new Writer {
      override def toString = pane.toString + ".writer"

      def close() = ()

      def flush() = ()

      def write(ch: Array[Char], off: Int, len: Int): Unit = {
        val str = new String(ch, off, len)
        textPane.append(str)
      }
    }

    // ---- OutputStream ----
    val outputStream: OutputStream = new OutputStream {
      override def toString = pane.toString + ".outputStream"

      override def write(b: Array[Byte], off: Int, len: Int): Unit = {
        val str = new String(b, off, len)
        textPane.append(str)
      }

      def write(b: Int): Unit = write(Array(b.toByte), 0, 1)
    }

    private val printStream = new PrintStream(outputStream, true)

    val component = new JScrollPane(textPane,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)

    private val popup = {
      val p = new JPopupMenu()
      p.add(new AbstractAction("Clear All") {
        override def actionPerformed(e: ActionEvent): Unit = clear()
      })
      p
    }

    def clear(): Unit = textPane.setText(null)

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
  def component: JComponent

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
