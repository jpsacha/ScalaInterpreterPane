/*
 *  CodePaneImpl.scala
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
package impl

import java.awt.{Color, Dimension}
import java.awt.event.{ActionListener, InputEvent, ActionEvent, KeyEvent}
import javax.swing.text.PlainDocument
import javax.swing.{AbstractAction, KeyStroke, ScrollPaneConstants, JScrollPane, JComponent, SwingUtilities, UIManager, UIDefaults, JEditorPane}

import de.sciss.scalainterpreter.actions.CompletionAction
import de.sciss.syntaxpane.components.Markers
import de.sciss.syntaxpane.syntaxkits.ScalaSyntaxKit
import de.sciss.syntaxpane.util.Configuration
import de.sciss.syntaxpane.{Token, SyntaxStyle, TokenType, SyntaxStyles, SyntaxView, DefaultSyntaxKit, SyntaxDocument}

import scala.collection.immutable.{Seq => ISeq}

object CodePaneImpl {
  import CodePane.{Config, ConfigBuilder, Range}

  def newConfigBuilder(): ConfigBuilder = new ConfigBuilderImpl

  def mkConfigBuilder(config: Config): ConfigBuilder = {
    import config._
    val b = new ConfigBuilderImpl
    b.text          = text
    b.keyMap        = keyMap
    b.keyProcessor  = keyProcessor
    b.font          = font
    b.style         = style
    b.preferredSize = preferredSize
    b
  }

  def apply(config: Config): CodePane = {
    initKit(config)
    val res = createPlain(config)
    res.init()
    res
  }

  private def put(cfg: Configuration, key: String, pair: (Color, Style.Face)): Unit = {
    val value = s"0x${(pair._1.getRGB | 0xFF000000).toHexString.substring(2)}, ${pair._2.code}"
    cfg.put(key, value)
  }

  private def put(cfg: Configuration, key: String, color: Color): Unit = {
    val value = s"0x${(color.getRGB | 0xFF000000).toHexString.substring(2)}"
    cfg.put(key, value)
  }

  private def initKit(config: Config): Unit = {
    DefaultSyntaxKit.initKit()
    // DefaultSyntaxKit.registerContentType("text/scala", "de.sciss.scalainterpreter.ScalaSyntaxKit")
    //      val synDef = DefaultSyntaxKit.getConfig( classOf[ DefaultSyntaxKit ])
    val syn = DefaultSyntaxKit.getConfig(classOf[ScalaSyntaxKit])
    val style = config.style
    put(syn, "Style.DEFAULT",           style.default)
    put(syn, "Style.KEYWORD",           style.keyword)
    put(syn, "Style.OPERATOR",          style.operator)
    put(syn, "Style.COMMENT",           style.comment)
    put(syn, "Style.NUMBER",            style.number)
    put(syn, "Style.STRING",            style.string)
    put(syn, "Style.STRING2",           style.string)
    put(syn, "Style.IDENTIFIER",        style.identifier)
    put(syn, "Style.DELIMITER",         style.delimiter)
    put(syn, "Style.TYPE",              style.tpe)

    put(syn, "LineNumbers.CurrentBack", style.lineBackground)
    put(syn, "LineNumbers.Foreground",  style.lineForeground)
    syn.put(SyntaxView.PROPERTY_SINGLE_COLOR_SELECT, style.singleColorSelect.toString) // XXX TODO currently broken - has no effect
    //      synDef.put( "SingleColorSelect", style.singleColorSelect.toString )
    put(syn, "SelectionColor",          style.selection)
    put(syn, "CaretColor",              style.caret)
    put(syn, "PairMarker.Color",        style.pair)

    // too bad - we need to override the default which is black here
    SyntaxStyles.getInstance().put(TokenType.DEFAULT, new SyntaxStyle(style.default._1, style.default._2.code))
  }

  private def createPlain(config: Config): Impl = {
    val ed: JEditorPane = new JEditorPane() {
      override protected def processKeyEvent(e: KeyEvent): Unit = super.processKeyEvent(config.keyProcessor(e))
    }
    ed.setPreferredSize(new Dimension(config.preferredSize._1, config.preferredSize._2))
    val style = config.style
    ed.setBackground(style.background) // stupid... this cannot be set in the kit config

    // fix for issue #8;
    // cf. http://stackoverflow.com/questions/15228336/changing-the-look-and-feel-changes-the-color-of-jtextpane
    if (UIManager.getLookAndFeel.getName.contains("Nimbus")) {
      val map = new UIDefaults()
      // none of these work... let's leave it that way,
      // the important thing is that the color is correct when
      // the pane becomes editable
      //
      // "EditorPane[Enabled].inactiveBackgroundPainter", "EditorPane[Enabled].inactiveBackground",
      // "EditorPane.inactiveBackgroundPainter", "EditorPane.inactiveBackground"
      map.put("EditorPane[Enabled].backgroundPainter", style.background)
      ed.putClientProperty("Nimbus.Overrides", map)
      SwingUtilities.updateComponentTreeUI(ed)
    }

    // this is very stupid: the foreground is not used by the syntax kit,
    // however the plain view compares normal and selected foreground. if
    // they are the same, it doesn't invoke drawSelectedText. therefore,
    // to achieve single color selection, we must ensure that the
    // foreground is _any_ color, as long as it is different from `style.foreground`.
    // fixes #3
    ed.setForeground(if (style.foreground == Color.white) Color.black else Color.white)
    ed.setSelectedTextColor(style.foreground)

    val iMap = ed.getInputMap(JComponent.WHEN_FOCUSED)
    val aMap = ed.getActionMap

    config.keyMap.iterator.zipWithIndex.foreach {
      case (spec, idx) =>
        val name = s"de.sciss.user$idx"
        iMap.put(spec._1, name)
        aMap.put(name, new AbstractAction {
          def actionPerformed(e: ActionEvent): Unit = spec._2.apply()
        })
    }

    new Impl(ed, config)
  }

  private final class ConfigBuilderImpl extends ConfigBuilder {
    var text          = ""
    var style: Style  = Style.BlueForest
    var keyMap        = Map.empty[KeyStroke, () => Unit]
    var keyProcessor  = identity: KeyEvent => KeyEvent
    var font          = Helper.defaultFonts
    var preferredSize = (500, 500)

    def build: Config = ConfigImpl(text, keyMap, keyProcessor, font, style, preferredSize)

    override def toString = s"CodePane.ConfigBuilder@${hashCode().toHexString}"
  }

  private final case class ConfigImpl(text: String, keyMap: Map[KeyStroke, () => Unit],
                                      keyProcessor: KeyEvent => KeyEvent, font: ISeq[(String, Int)],
                                      style: Style, preferredSize: (Int, Int))
    extends Config {
    override def toString = s"CodePane.Config@${hashCode().toHexString}"
  }

  private final class Impl(val editor: JEditorPane, config: Config) extends CodePane {
    val component: JComponent = new JScrollPane(editor,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS)

    private val execMarker = new ExecMarker(editor)

    def docOption: Option[SyntaxDocument] = {
      val doc = editor.getDocument
      // if (doc == null) return None
      doc match {
        case sd: SyntaxDocument => Some(sd)
        case _ => None
      }
    }

    def init(): Unit = {
      editor.setContentType("text/scala")
      editor.setText(config.text)
      editor.setFont(Helper.createFont(config.font))
      val doc = editor.getDocument
      doc.putProperty(PlainDocument.tabSizeAttribute, 2)
      doc match {
        case synDoc: SyntaxDocument => synDoc.clearUndos()
        case _ =>
      }
    }

    def selectedText: Option[String] = {
      val txt = editor.getSelectedText
      if (txt != null) Some(txt) else None
    }

    def currentTextLine: Option[String] = docOption.map(_.getLineAt(editor.getCaretPosition))

    def activeText: Option[String] = selectedText orElse currentTextLine

    def selectedRange: Option[Range] = {
      val start = editor.getSelectionStart
      val end   = editor.getSelectionEnd
      if (start < end) Some(Range(start, end, selected = true)) else None
    }

    def currentLineRange: Option[Range] = docOption.flatMap { doc =>
      val pos   = editor.getCaretPosition
      val start = doc.getLineStartOffset(pos)
      val end   = doc.getLineEndOffset  (pos)
      if (start < end) Some(Range(start, end, selected = false)) else None
    }

    def activeRange: Option[Range] = selectedRange orElse currentLineRange

    def flash(range: Range): Unit = execMarker.install(range)

    def abortFlash(): Unit = execMarker.abort()

    def activeToken: Option[Token] = docOption.flatMap { doc =>
      val pos = editor.getCaretPosition
      Option(doc.getTokenAt(pos))
    }

    def getTextSlice(range: Range): String = editor.getDocument.getText(range.start, range.length)

    def installAutoCompletion(interpreter: Interpreter): Unit = {
      val iMap = editor.getInputMap(JComponent.WHEN_FOCUSED)
      val aMap = editor.getActionMap
      iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), "de.sciss.comp")
      aMap.put("de.sciss.comp", new CompletionAction(interpreter.completer))
    }

    override def toString = s"CodePane@${hashCode().toHexString}"
  }

  // ---- exec marker ----

  private val fullColor       = new Color(0xFF, 0x7F, 0x00, 0xFF)
  private val emptyColor      = new Color(0xFF, 0x7F, 0x00, 0x00)

  private val fullAbortColor  = new Color(0xFF, 0x00, 0x00, 0xFF)
  private val emptyAbortColor = new Color(0xFF, 0x00, 0x00, 0x00)

  private final class ExecMarker(editor: JEditorPane)
    extends Markers.SimpleMarker(null) with ActionListener {

    private var added   = false
    private val timer   = new javax.swing.Timer(50, this)
    private var colrIdx = 0

    private var _range : Range  = _
    private var tag    : AnyRef = _
    private var stopColor: Color  = _
    private var startColor: Color = _

    private var currentColor  = fullColor

    override def getColor: Color = currentColor

    private def updateColor(): Unit = {
      val w0 = colrIdx
      val w1 = 9 - colrIdx
      val r  = (startColor.getRed   * w0 + stopColor.getRed   * w1) / 9
      val g  = (startColor.getGreen * w0 + stopColor.getGreen * w1) / 9
      val b  = (startColor.getBlue  * w0 + stopColor.getBlue  * w1) / 9
      val a  = (startColor.getAlpha * w0 + stopColor.getAlpha * w1) / 9
      currentColor = new Color(r, g, b, a)
    }

    private def updateHighlight(): Unit = {
      updateColor()
      val hil = editor.getHighlighter
      hil.changeHighlight(tag, _range.start, _range.stop)
    }

    def actionPerformed(e: ActionEvent): Unit = if (added) {
      colrIdx -= 1
      if (colrIdx >= 0) {
        updateHighlight()
      } else {
        timer.stop()
        remove()
      }
    }

    def abort(): Unit = if (added) {
      startColor  = fullAbortColor
      if (!_range.selected) stopColor = emptyAbortColor
      updateHighlight()
    }

    def install(range: Range): Unit = {
      remove()
      _range    = range
      val hil   = editor.getHighlighter
      colrIdx   = 9
      startColor  = fullColor
      stopColor   = if (_range.selected) editor.getSelectionColor else emptyColor // dirty...
      if (_range.selected) editor.setSelectionColor(emptyColor)
      updateColor()
      tag       = hil.addHighlight(range.start, range.stop, this)
      timer.restart()
      added   = true
    }

    def remove(): Unit = if (added) {
      timer.stop()
      if (_range.selected) editor.setSelectionColor(stopColor) // dirty...
      val hil = editor.getHighlighter
      hil.removeHighlight(tag)
      added = false
    }
  }
}
