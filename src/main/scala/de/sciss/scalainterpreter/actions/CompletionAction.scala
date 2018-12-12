/*
 *  CompletionAction.scala
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
package actions

import java.awt.Dialog.ModalityType
import java.awt.Point
import java.awt.event.ActionEvent

import de.sciss.scalainterpreter.Completion.Candidate
import de.sciss.scalainterpreter.actions.CompletionAction.Replace
import de.sciss.swingplus.ListView
import de.sciss.syntaxpane.SyntaxDocument
import de.sciss.syntaxpane.actions.DefaultSyntaxAction
import de.sciss.syntaxpane.actions.gui.EscapeListener
import de.sciss.syntaxpane.util.{StringUtils, SwingUtils}
import javax.swing.event.{DocumentEvent, DocumentListener}
import javax.swing.text.{BadLocationException, JTextComponent}
import javax.swing.{DefaultListCellRenderer, GroupLayout, JDialog, SwingUtilities}

import scala.swing.event.{Key, KeyPressed, MouseClicked}
import scala.swing.{Component, ScrollPane, TextField}

object CompletionAction {
  private final val escapeChars = ";(= \t\n\r"

  private final case class Replace(candidate: Candidate, append: String = "") {
    def fullString: String = s"${candidate.fullString}$append"
  }

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

    private[this] var items     = List.empty[Candidate]
    private[this] var succeed   = (_: Option[Replace]) => ()

    private[this] val ggText: TextField = new TextField {
      border = null
      listenTo(keys)
      reactions += {
        case e: KeyPressed => dlg.keyPressed(e)
      }
    }

    private[this] val ggList: ListView[Candidate] = new ListView[Candidate] {
      listenTo(mouse.clicks)
      reactions += {
        case e: MouseClicked  => dlg.mouseClicked(e)
      }
      renderer = new ListView.Renderer[Candidate] {
        private[this] val peer = new DefaultListCellRenderer
        def componentFor(list: ListView[_], isSelected: Boolean, focused: Boolean, a: Candidate,
                         index: Int): Component = {
          // XXX TODO -- not nice
          val j = peer.getListCellRendererComponent(list.peer.asInstanceOf[javax.swing.JList[_]],
            a.fullString, index, isSelected, focused).asInstanceOf[javax.swing.JComponent]
          Component.wrap(j)
        }
      }

      selection.intervalMode = ListView.IntervalMode.Single
      focusable = false
    }

    private[this] val ggScroll  = new ScrollPane(ggList)
    ggScroll.peer.putClientProperty("styleId", "undecorated")

    peer.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE)
    resizable = false
    peer.setUndecorated(true)

    //    contents = new GroupPanel {
    //      horizontal  = Par(GroupPanel.Alignment.Leading)(ggText, ggScroll)
    //      vertical    = Par(GroupPanel.Alignment.Leading)(ggText, /* ???, */ ggScroll)
    //    }

    // XXX TODO - should use GroupPanel, but too lazy to read up on the constraints
    private[this] val lay = new GroupLayout(peer.getContentPane)
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

    def show(abbrev: String, items: List[Candidate])
            (succeed: Option[Replace] => Unit): Unit = try {
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
      case _: BadLocationException => // ignore for now
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
      val filtered  = items.filter(x => /* x.nonEmpty && */ StringUtils.camelCaseMatch(x.name, prefix))
      ggList.items  = filtered
      if (idx >= 0 && filtered.contains(selected)) {
        selectedIndex = idx
        ggList.ensureIndexIsVisible(idx)
      } else {
        selectedIndex = 0
      }
    }

    private def finish(result: Option[Replace]): Unit = {
      // target.replaceSelection(result)
      succeed(result)
      visible = false
    }

    private def keyPressed(e: KeyPressed): Unit =
      ggList.selection.indices.headOption.foreach { i =>
      	val ch = e.peer.getKeyChar
      	e.key match {
      	  case Key.Escape => finish(None)

       	  case Key.Down if i < ggList.model.size - 1 =>
            val i1 = i + 1
            selectedIndex_=(i1)
            ggList.ensureIndexIsVisible(i1)

          case Key.Up if i > 0 =>
            val i1 = i - 1
            selectedIndex_=(i1)
            ggList.ensureIndexIsVisible(i1)

          case _ if escapeChars.indexOf(ch) >= 0 =>
            val result0 = if (selectedIndex >= 0) {
              selectedItem
            } else {
              Completion.Simple(ggText.text)
            }
            val append = if (ch == '\n') "" else {
              if (ch == '\t') " " else ch.toString
            }
            finish(Some(Replace(result0, append)))

          case _ =>
        }
      }

    private def selectedItem     : Candidate= ggList.selection.items  .headOption.orNull
    private def selectedIndex    : Int        = ggList.selection.indices.headOption.getOrElse(-1)
    private def selectedIndex_=(i: Int): Unit = ggList.selectIndices(i)

    private def mouseClicked(e: MouseClicked): Unit = {
      if (e.clicks == 2) {
        val selected = selectedItem
        targetJ.replaceSelection(selected.fullString)
        visible = false
      }
    }
  }
}
class CompletionAction(completer: Completer) extends DefaultSyntaxAction("COMPLETION") {
  private[this] var dlg: CompletionAction.Dialog = _

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

    val cwLen       = cw.length()
    val m           = completer.complete(cw, cwLen)
    val candidates = m.candidates
    if (candidates.isEmpty) return

    println(s"candidates.size = ${candidates.size}")
    candidates.foreach(println)

    val off = start + m.cursor

    val head :: tail = candidates

    val common = head match {
      case Completion.Simple(_) => 0
      case _ =>
        val headF = head.fullString
        val comH0 = headF.indexOf('[')
        val comH1 = if (comH0 >= 0) comH0 else headF.length
        val comH2 = headF.indexOf('(')
        val comH  = if (comH2 >= 0) math.min(comH1, comH2) else comH1
        (comH /: tail) { (len, s1) =>
          val s1F = s1.fullString
          val m1  = math.min(len, s1F.length)
          var m2  = 0
          while (m2 < m1 && s1F.charAt(m2) == headF.charAt(m2)) m2 += 1
          m2
        }
    }

    target.select(off - common, start + cwLen)

    def perform(replace: Replace): Unit = {
      val replace0 = replace.fullString
      val replace1 = removeTypes(replace0, 0)
      val p0 = target.getSelectionStart
      target.replaceSelection(replace1)
      val i = replace1.indexOf('(') + 1
      if (i > 0) {
        val j = replace1.indexOf(',', i)
        val k = replace1.indexOf(')', i)
        val m = math.min(j, k)
        target.select(p0 + i, p0 + m)
      }
    }

    // println(s"off = $off, start = $start, cwlen = $cwlen, common $common")

    candidates match {
      case one :: Nil => perform(Replace(one))

      case _ /* more */ =>
        if (dlg == null) dlg = new CompletionAction.Dialog(target)

        dlg.show(cw.substring(m.cursor), candidates) {
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
