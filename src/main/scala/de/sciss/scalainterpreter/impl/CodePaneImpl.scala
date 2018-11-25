/*
 *  CodePaneImpl.scala
 *  (ScalaInterpreterPane)
 *
 *  Copyright (c) 2010-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.scalainterpreter
package impl

import java.awt.Color
import java.awt.event.{ActionEvent, ActionListener, InputEvent, KeyEvent}
import javax.swing.text.PlainDocument
import javax.swing.{AbstractAction, JComponent, JEditorPane, KeyStroke, SwingUtilities, UIDefaults, UIManager}

import de.sciss.scalainterpreter.actions.CompletionAction
import de.sciss.syntaxpane.components.{LineNumbersRuler, Markers}
import de.sciss.syntaxpane.syntaxkits.ScalaSyntaxKit
import de.sciss.syntaxpane.util.Configuration
import de.sciss.syntaxpane.{DefaultSyntaxKit, SyntaxDocument, SyntaxStyle, SyntaxStyles, SyntaxView, Token, TokenType}

import scala.collection.immutable.{Seq => ISeq}
import scala.reflect.ClassTag
import scala.swing.Swing._
import scala.swing.{Action, Component, EditorPane, ScrollPane}

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
    initScalaKit(config)
    val res = createPlain(config)
    res.init()
  }

  private def put(cfg: Configuration, key: String, pair: (Color, Style.Face)): Unit = {
    val value = s"0x${(pair._1.getRGB | 0xFF000000).toHexString.substring(2)}, ${pair._2.code}"
    cfg.put(key, value)
  }

  private def put(cfg: Configuration, key: String, color: Color): Unit = {
    val value = s"0x${(color.getRGB | 0xFF000000).toHexString.substring(2)}"
    cfg.put(key, value)
  }

  private def isDark: Boolean = UIManager.getBoolean("dark-skin")

  def initKit[A <: DefaultSyntaxKit](style: Style = if (isDark) Style.BlueForest else Style.Light)
                                    (implicit ct: ClassTag[A]): Unit = {
    //    DefaultSyntaxKit.initKit()
    val syn   = DefaultSyntaxKit.getConfig(ct.runtimeClass.asInstanceOf[Class[A]])
    put(syn, "Style.DEFAULT",     style.default)
    put(syn, "Style.KEYWORD",     style.keyword)
    put(syn, "Style.KEYWORD2",    style.keyword2)
    put(syn, "Style.OPERATOR",    style.operator)
    put(syn, "Style.COMMENT",     style.comment)
    put(syn, "Style.NUMBER",      style.number)
    put(syn, "Style.STRING",      style.string)
    put(syn, "Style.STRING2",     style.string)
    put(syn, "Style.IDENTIFIER",  style.identifier)
    put(syn, "Style.DELIMITER",   style.delimiter)
    put(syn, "Style.TYPE",        style.tpe)
    put(syn, "Style.TYPE2",       style.tpeStd)
    put(syn, "Style.TYPE3",       style.tpeUser)

    put(syn, LineNumbersRuler.PROPERTY_CURRENT_BACK, style.lineBackground)
    put(syn, LineNumbersRuler.PROPERTY_FOREGROUND  , style.lineForeground)
    syn.put(SyntaxView.PROPERTY_SINGLE_COLOR_SELECT, style.singleColorSelect.toString) // XXX TODO currently broken - has no effect
    //      synDef.put( "SingleColorSelect", style.singleColorSelect.toString )
    put(syn, "SelectionColor",    style.selection)
    put(syn, "CaretColor",        style.caret)
    put(syn, "PairMarker.Color",  style.pair)

    if (isDark) put(syn, LineNumbersRuler.PROPERTY_BACKGROUND, UIManager.getColor("Panel.background"))

    // too bad - we need to override the default which is black here
    SyntaxStyles.getInstance().put(TokenType.DEFAULT, new SyntaxStyle(style.default._1, style.default._2.code))
  }

  private def initScalaKit(config: Config): Unit = {
    // DefaultSyntaxKit.registerContentType("text/scala", "de.sciss.scalainterpreter.ScalaSyntaxKit")
    //      val synDef = DefaultSyntaxKit.getConfig( classOf[ DefaultSyntaxKit ])
    initKit[ScalaSyntaxKit](config.style)
  }

  def createEditorPane(style: Style = if (isDark) Style.BlueForest else Style.Light,
                       preferredSize: (Int, Int) = (500, 500),
                       keyProcessor: KeyEvent => KeyEvent = identity,
                       keyMap: Map[KeyStroke, () => Unit] = Map.empty): EditorPane = {
    val _preferredSize = preferredSize
    val ed: EditorPane = new EditorPane("text/plain", "") {
      override lazy val peer: JEditorPane = new JEditorPane("text/plain", "") with SuperMixin {
        override protected def processKeyEvent(e: KeyEvent): Unit = super.processKeyEvent(keyProcessor(e))
      }

      preferredSize = _preferredSize
      background    = style.background   // stupid... this cannot be set in the kit config
    }

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
      ed.peer.putClientProperty("Nimbus.Overrides", map)
      SwingUtilities.updateComponentTreeUI(ed.peer)
    }

    // this is very stupid: the foreground is not used by the syntax kit,
    // however the plain view compares normal and selected foreground. if
    // they are the same, it doesn't invoke drawSelectedText. therefore,
    // to achieve single color selection, we must ensure that the
    // foreground is _any_ color, as long as it is different from `style.foreground`.
    // fixes #3
    ed.foreground = if (style.foreground == Color.white) Color.black else Color.white
    val edJ = ed.peer
    edJ.setSelectedTextColor(style.foreground)

    val iMap = edJ.getInputMap(JComponent.WHEN_FOCUSED)
    val aMap = edJ.getActionMap

    if (keyMap.nonEmpty) keyMap.iterator.zipWithIndex.foreach {
      case (spec, idx) =>
        val name = s"de.sciss.user$idx"
        iMap.put(spec._1, name)
        aMap.put(name, new AbstractAction {
          def actionPerformed(e: ActionEvent): Unit = spec._2.apply()
        })
    }

    ed
  }

  private def createPlain(config: Config): Impl = {
    val ed = createEditorPane(style = config.style, preferredSize = config.preferredSize,
      keyProcessor = config.keyProcessor, keyMap = config.keyMap)

    new Impl(ed, config)
  }

  private final class ConfigBuilderImpl extends ConfigBuilder {
    var text          : String                      = ""
    var style         : Style                       = if (isDark) Style.BlueForest else Style.Light
    var keyMap        : Map[KeyStroke, () => Unit]  = Map.empty
    var keyProcessor  : KeyEvent => KeyEvent        = identity
    var font          : ISeq[(String, Int)]         = Fonts.defaultFonts
    var preferredSize : (Int, Int)                  = (500, 500)

    def build: Config = ConfigImpl(text, keyMap, keyProcessor, font, style, preferredSize)

    override def toString = s"CodePane.ConfigBuilder@${hashCode().toHexString}"
  }

  private final case class ConfigImpl(text: String, keyMap: Map[KeyStroke, () => Unit],
                                      keyProcessor: KeyEvent => KeyEvent, font: ISeq[(String, Int)],
                                      style: Style, preferredSize: (Int, Int))
    extends Config {
    override def toString = s"CodePane.Config@${hashCode().toHexString}"
  }

  trait Basic {
    // ---- abstract ----

    protected def editor      : EditorPane
    protected def mimeType    : String
    protected def initialText : String
    protected def fonts       : Fonts.List
    protected def tabSize     : Int

    // ---- impl ----

    import de.sciss.swingplus.Implicits._

    final def undoAction: Action = Action.wrap(editor.peer.getActionMap.get("undo"))
    final def redoAction: Action = Action.wrap(editor.peer.getActionMap.get("redo"))

    final def clearUndoHistory(): Unit =
      editor.peer.getDocument match {
        case sd: SyntaxDocument => sd.clearUndos()
        case _ =>
      }

    private[this] final lazy val _scroll: ScrollPane = {
      val res = new ScrollPane(editor)
      res.horizontalScrollBarPolicy = ScrollPane.BarPolicy.AsNeeded
      res.verticalScrollBarPolicy   = ScrollPane.BarPolicy.Always
      res.peer.putClientProperty("styleId", "undecorated")
      res
    }

    protected def scroll: ScrollPane = _scroll

    def component: Component = scroll

    private[this] val execMarker = new ExecMarker(editor)

    final def docOption: Option[SyntaxDocument] = {
      val doc = editor.peer.getDocument
      // if (doc == null) return None
      doc match {
        case sd: SyntaxDocument => Some(sd)
        case _ => None
      }
    }

    final def init(): this.type = {
      editor.contentType  = mimeType
      editor.text         = initialText
      editor.font         = Fonts.create(fonts)
      val doc = editor.peer.getDocument
      doc.putProperty(PlainDocument.tabSizeAttribute, tabSize)
      doc match {
        case synDoc: SyntaxDocument => synDoc.clearUndos()
        case _ =>
      }
      this
    }

    final def selectedText: Option[String] = {
      val txt = editor.selected
      Option(txt)
    }

    final def currentTextLine: Option[String] = docOption.map(_.getLineAt(editor.caret.position))

    final def activeText: Option[String] = selectedText orElse currentTextLine

    final def selectedRange: Option[Range] = {
      val edJ   = editor.peer
      val start = edJ.getSelectionStart
      val end   = edJ.getSelectionEnd
      if (start < end) Some(Range(start, end, selected = true)) else None
    }

    final def currentLineRange: Option[Range] = docOption.flatMap { doc =>
      val pos   = editor.caret.position
      val start = doc.getLineStartOffset(pos)
      val end   = doc.getLineEndOffset  (pos)
      if (start < end) Some(Range(start, end, selected = false)) else None
    }

    final def activeRange: Option[Range] = selectedRange orElse currentLineRange

    final def flash(range: Range): Unit = execMarker.install(range)

    final def abortFlash(): Unit = execMarker.abort()

    final def activeToken: Option[Token] = docOption.flatMap { doc =>
      val pos = editor.caret.position
      Option(doc.getTokenAt(pos))
    }

    final def getTextSlice(range: Range): String = editor.peer.getDocument.getText(range.start, range.length)

    final def installAutoCompletion(interpreter: Interpreter): Unit = {
      val iMap = editor.peer.getInputMap(JComponent.WHEN_FOCUSED)
      val aMap = editor.peer.getActionMap
      iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), "de.sciss.comp")
      aMap.put("de.sciss.comp", new CompletionAction(interpreter.completer))
    }
  }

  private final class Impl(val editor: EditorPane, config: Config) extends Basic with CodePane {

    protected def mimeType    : String      = "text/scala"
    protected def initialText : String      = config.text
    protected def fonts       : Fonts.List  = config.font
    protected def tabSize     : Int         = 2

    override def toString = s"CodePane@${hashCode().toHexString}"
  }

  // ---- exec marker ----

  private val fullColor       = new Color(0xFF, 0x7F, 0x00, 0xFF)
  private val emptyColor      = new Color(0xFF, 0x7F, 0x00, 0x00)

  private val fullAbortColor  = new Color(0xFF, 0x00, 0x00, 0xFF)
  private val emptyAbortColor = new Color(0xFF, 0x00, 0x00, 0x00)

  private final class ExecMarker(editor: EditorPane)
    extends Markers.SimpleMarker(null) with ActionListener {

    private[this] var added   = false
    private[this] val timer   = new javax.swing.Timer(50, this)
    private[this] var colrIdx = 0

    private[this] var _range    : Range   = _
    private[this] var tag       : AnyRef  = _
    private[this] var stopColor : Color   = _
    private[this] var startColor: Color   = _

    private[this] var currentColor  = fullColor

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
      val hil = editor.peer.getHighlighter
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
      val hil   = editor.peer.getHighlighter
      colrIdx   = 9
      startColor  = fullColor
      stopColor   = if (_range.selected) editor.peer.getSelectionColor else emptyColor // dirty...
      if (_range.selected) editor.peer.setSelectionColor(emptyColor)
      updateColor()
      tag       = hil.addHighlight(range.start, range.stop, this)
      timer.restart()
      added   = true
    }

    def remove(): Unit = if (added) {
      timer.stop()
      if (_range.selected) editor.peer.setSelectionColor(stopColor) // dirty...
      val hil = editor.peer.getHighlighter
      hil.removeHighlight(tag)
      added = false
    }
  }
}
