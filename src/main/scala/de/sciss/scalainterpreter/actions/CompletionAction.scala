/*
 *  CompletionAction.scala
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
package actions

import javax.swing.text.{BadLocationException, JTextComponent}
import de.sciss.swingplus.ListView

import scala.swing.event.{Key, MouseClicked, KeyPressed}
import scala.swing.{ScrollPane, TextField}
import tools.nsc.interpreter.Completion.ScalaCompleter
import java.awt.Dialog.ModalityType
import javax.swing.event.{DocumentListener, DocumentEvent}
import java.awt.Point
import javax.swing.{GroupLayout, JDialog, SwingUtilities}
import java.awt.event.ActionEvent
import de.sciss.syntaxpane.actions.gui.EscapeListener
import de.sciss.syntaxpane.util.{StringUtils, SwingUtils}
import de.sciss.syntaxpane.SyntaxDocument
import de.sciss.syntaxpane.actions.DefaultSyntaxAction

object CompletionAction {
  private final val escapeChars = ";(= \t\n\r"
  private final val defPrefix   = "def "

  private class Dialog(targetJ: JTextComponent)
    extends scala.swing.Dialog() {
    dlg =>

    private trait Mix extends EscapeListener with InterfaceMixin {
      def escapePressed(): Unit = visible = false
    }

    override lazy val peer: JDialog with EscapeListener with InterfaceMixin = {
      val owner = SwingUtilities.getWindowAncestor(targetJ)
      if (owner == null) new JDialog with Mix with SuperMixin
      else owner match {
        case f: java.awt.Frame  =>
          new JDialog(f, ModalityType.APPLICATION_MODAL) with Mix with SuperMixin
        case d: java.awt.Dialog =>
          new JDialog(d, ModalityType.APPLICATION_MODAL) with Mix with SuperMixin
      }
    }

    private var items     = List.empty[String]
    private var succeed   = (_: Option[String]) => ()

    private val ggText: TextField = new TextField {
      border = null
      listenTo(keys)
      reactions += {
        case e: KeyPressed => dlg.keyPressed(e)
      }
    }

    private val ggList: ListView[String] = new ListView[String] {
      listenTo(mouse.clicks)
      reactions += {
        case e: MouseClicked  => dlg.mouseClicked(e)
      }

      selection.intervalMode = ListView.IntervalMode.Single
      focusable = false
    }

    private val ggScroll  = new ScrollPane(ggList)

    peer.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE)
    resizable = false
    peer.setUndecorated(true)

    //    contents = new GroupPanel {
    //      horizontal  = Par(GroupPanel.Alignment.Leading)(ggText, ggScroll)
    //      vertical    = Par(GroupPanel.Alignment.Leading)(ggText, /* ???, */ ggScroll)
    //    }

    // XXX TODO - should use GroupPanel, but too lazy to read up on the constraints
    private val lay = new GroupLayout(peer.getContentPane)
    lay.setHorizontalGroup(lay.createParallelGroup(GroupLayout.Alignment.LEADING)
      .addComponent(ggText  .peer, javax.swing.GroupLayout.DEFAULT_SIZE, 375, Short.MaxValue)
      .addComponent(ggScroll.peer, javax.swing.GroupLayout.DEFAULT_SIZE, 375, Short.MaxValue))
    lay.setVerticalGroup(lay.createParallelGroup(GroupLayout.Alignment.LEADING)
      .addGroup(lay.createSequentialGroup
        .addComponent(ggText.peer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, 0)
        .addComponent(ggScroll.peer, GroupLayout.DEFAULT_SIZE, 111, Short.MaxValue)
      )
    )
    pack()
    ggText.peer.getDocument.addDocumentListener(new DocumentListener {
      def insertUpdate (e: DocumentEvent): Unit = reFilterList()
      def removeUpdate (e: DocumentEvent): Unit = reFilterList()
      def changedUpdate(e: DocumentEvent): Unit = reFilterList()
    })
    ggText.peer.setFocusTraversalKeysEnabled(false)
    SwingUtils.addEscapeListener(peer)

    def show(abbrev: String, items: List[String])(succeed: Option[String] => Unit): Unit = try {
      this.items    = items
      this.succeed  = succeed

      val window    = SwingUtilities.getWindowAncestor(targetJ)
      val rt        = targetJ.modelToView(targetJ.getSelectionStart)
      val loc       = new Point(rt.x, rt.y)
      peer.setLocationRelativeTo(window)
      val loc1      = SwingUtilities.convertPoint(targetJ, loc, window)
      SwingUtilities.convertPointToScreen(loc1, window)
      location      = loc1
    } catch {
      case ex: BadLocationException => // ignore for now
    } finally {
      val font = targetJ.getFont
      ggText.font = font
      ggList.font = font
      peer.doLayout()
      ggText.text = abbrev
      reFilterList()
      visible     = true
    }

    private def reFilterList(): Unit = {
      val prefix    = ggText.text
      val idx       = selectedIndex
      val selected  = selectedItem
      val filtered  = items.filter(StringUtils.camelCaseMatch(_, prefix))
      ggList.items  = filtered
      if (idx >= 0 && filtered.contains(selected)) {
        selectedIndex = idx
        ggList.ensureIndexIsVisible(idx)
      } else {
        selectedIndex = 0
      }
    }

    private def finish(result: Option[String]): Unit = {
      // target.replaceSelection(result)
      succeed(result)
      visible = false
    }

    private def keyPressed(e: KeyPressed): Unit = {
      ggList.selection.indices.headOption.map { i => 
      	val ch = e.peer.getKeyChar
      	e.key match {
      	  case Key.Escape => finish(None)

       	  case Key.Down if i < ggList.model.size - 1 =>
            val i1 = i + 1
            selectedIndex = i1
            ggList.ensureIndexIsVisible(i1)

          case Key.Up if i > 0 =>
            val i1 = i - 1
            selectedIndex = i1
            ggList.ensureIndexIsVisible(i1)

          case _ if escapeChars.indexOf(ch) >= 0 =>
            val result0 = if (selectedIndex >= 0) {
              selectedItem
            } else {
              ggText.text
            }
            val result = if (ch == '\n') result0 else {
              result0 + (if (ch == '\t') ' ' else ch)
            }
            finish(Some(result))

          case _ =>
        }
      }
    }
    private def selectedItem  = ggList.selection.items  .headOption.orNull
    private def selectedIndex = ggList.selection.indices.headOption.getOrElse(-1)
    private def selectedIndex_=(i: Int): Unit = ggList.selectIndices(i)

    private def mouseClicked(e: MouseClicked): Unit = {
      if (e.clicks == 2) {
        val selected = selectedItem
        targetJ.replaceSelection(selected)
        visible = false
      }
    }
  }
}
class CompletionAction(completer: ScalaCompleter) extends DefaultSyntaxAction("COMPLETION") {
  import CompletionAction.defPrefix

  private var dlg: CompletionAction.Dialog = null

  override def actionPerformed(target: JTextComponent, sDoc: SyntaxDocument, dot: Int, e: ActionEvent): Unit = {
    val (cw, start) = {
      val sel = target.getSelectedText
      if (sel != null) {
        (sel, target.getSelectionStart)
      } else {
        val line  = sDoc.getLineAt(dot)
        val start = sDoc.getLineStartOffset(dot)
        // val stop = sDoc.getLineEndOffset( dot )
        (line.substring(0, dot - start), start)
      }
    }

    val cwlen = cw.length()
    val m     = completer.complete(cw, cwlen)
    val cand  = m.candidates
    if (cand.isEmpty) return

    val off = start + m.cursor

    val hasDef = cand.exists(_.startsWith(defPrefix))

    val more1 @ (head :: tail) = if (!hasDef) cand else cand.map {
      case x if x.startsWith(defPrefix) => x.substring(4)  // cheesy way of handling the 'def'
      case x => x
    }

    val common  = if (!hasDef) 0 else {
      val comh0   = head.indexOf('[')
      val comh1   = if (comh0 >= 0) comh0 else head.length
      val comh2   = head.indexOf('(')
      val comh3   = if (comh2 >= 0) math.min(comh1, comh2) else comh1
      val comh4   = head.indexOf(':')
      val comh    = if (comh4 >= 0) math.min(comh4, comh3) else comh3
      // println(s"Head '$head' comh0 $comh0 comh1 $comh1 comh $comh")
      (comh /: tail) { (len, s1) =>
        val m1 = math.min(len, s1.length)
        var m2 = 0
        while (m2 < m1 && s1.charAt(m2) == head.charAt(m2)) m2 += 1
        m2
      }
    }

    target.select(off - common, start + cwlen)

    def perform(replc: String): Unit = {
      val replc1 = removeTypes(replc, 0)
      val p0 = target.getSelectionStart
      target.replaceSelection(replc1)
      val i = replc1.indexOf('(') + 1
      if (i > 0) {
        val j = replc1.indexOf(',', i)
        val k = replc1.indexOf(')', i)
        val m = math.min(j, k)
        target.select(p0 + i, p0 + m)
      }
    }

    // println(s"off = $off, start = $start, cwlen = $cwlen, common $common")

    more1 match {
      case one :: Nil => perform(one)

      case more =>
        if (dlg == null) dlg = new CompletionAction.Dialog(target)

        dlg.show(cw.substring(m.cursor), more1) {
          case Some(result) =>
            // println(s"Result: '$result'")
            perform(result)
          case _ => // aborted
            target.setSelectionStart(target.getSelectionEnd)
        }
    }
  }

  private def removeTypes(s: String, i0: Int): String = {
    val i   = s.indexOf('[', i0)
    val j   = s.indexOf('(', i0)
    if (i >= 0 && i < j) {      // type parameter
      val k  = s.indexOf(']', i + 1)
      removeTypes(s.substring(0, i) + s.substring(k + 1), i0)
    } else if (j >= 0) {
      val k = s.indexOf(')', j + 1)
      val m = s.indexOf(':', j + 1)
      if (m >= 0 && m < k) {
        val n     = s.indexOf(',', m + 1)
        val pre   = s.substring(0, m)
        val post  = s.substring(if (n >= 0 && n < k) n else k)
        removeTypes(pre + post, i0)
      } else if (m == k + 1) {
        s.substring(0, m)
      } else if (k >= 0 /* && k < s.length - 1 && s.charAt(k + 1) == '(' */ ) {  // multiple argument lists
        removeTypes(s, k + 1)
      } else {
        s
      }
    } else {
      val m = s.indexOf(':', i0)
      if (m >= 0) s.substring(0, m) else s
    }
  }
}
