/*
 *  InterpreterPaneImpl.scala
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

import java.awt.EventQueue
import java.awt.event.{InputEvent, KeyEvent, ActionEvent}
import javax.swing.{KeyStroke, AbstractAction, JComponent}
import scala.swing.Swing
import Swing._

import de.sciss.scalainterpreter.Interpreter.Result
import de.sciss.swingplus.OverlayPanel

import scala.concurrent.{ExecutionContext, Future}
import scala.swing.{Orientation, BoxPanel, Label, Component, ProgressBar, BorderPanel, Panel}

object InterpreterPaneImpl {
  import InterpreterPane.{Config, ConfigBuilder}

  def newConfigBuilder(): ConfigBuilder = new ConfigBuilderImpl

  def mkConfigBuilder(config: Config): ConfigBuilder = {
    import config._
    val b = new ConfigBuilderImpl
    b.executeKey            = executeKey
    b.code                  = code
    b.prependExecutionInfo  = prependExecutionInfo
    b
  }

  def bang(codePane: CodePane, interpreter: Interpreter): Option[Interpreter.Result] =
    codePane.activeRange.map { range =>
      val text  = codePane.getTextSlice(range)
      codePane.flash(range)
      val res   = interpreter.interpret(text)
      if (!res.isSuccess) codePane.abortFlash()
      res
    }

  def wrap(interpreter: Interpreter, codePane: CodePane): InterpreterPane =
    create(Config().build, Future.successful(interpreter), codePane)(ExecutionContext.global)

  def wrapAsync(interpreter: Future[Interpreter], codePane: CodePane)
               (implicit exec: ExecutionContext): InterpreterPane =
    create(Config().build, interpreter, codePane)

  def apply(config: Config, interpreterConfig: Interpreter.Config, codePaneConfig: CodePane.Config)
           (implicit exec: ExecutionContext): InterpreterPane = {

    val cpSet     = if (config.prependExecutionInfo) incorporate(config, codePaneConfig) else codePaneConfig
    val codePane  = CodePane(cpSet)
    val fut       = Interpreter.async(interpreterConfig)
    create(config, fut, codePane)
  }

  private final class ConfigBuilderImpl extends ConfigBuilder {
    var executeKey            = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK)
    var code                  = ""
    var prependExecutionInfo  = true

    def build: Config = ConfigImpl(executeKey, code, prependExecutionInfo)

    override def toString = s"InterpreterPane.ConfigBuilder@${hashCode().toHexString}"
  }

  private final case class ConfigImpl(executeKey: KeyStroke, code: String, prependExecutionInfo: Boolean)
    extends Config {

    override def toString = s"InterpreterPane.Config@${hashCode().toHexString}"
  }

  private def create(config: Config, fut: Future[Interpreter], codePane: CodePane)
                    (implicit exec: ExecutionContext): InterpreterPane = {
    val impl = new Impl(config, fut, codePane)
    fut.onSuccess { case in =>
      EventQueue.invokeLater(new Runnable {
        def run(): Unit = impl.setInterpreter(in)
      })
    }
    impl
  }

  private def incorporate(config: Config, code: CodePane.Config): CodePane.Config = {
    val res = CodePane.ConfigBuilder(code)
    val keyMod = KeyEvent.getKeyModifiersText(config.executeKey.getModifiers)
    val keyTxt = KeyEvent.getKeyText(config.executeKey.getKeyCode)
    res.text =
      s"""// Type Scala code here.
         |// Press '$keyMod + $keyTxt' to execute selected text
         |// or current line.
         |
         |${res.text}""".stripMargin
    res.build
  }

  private final class Impl(config: Config, interpreter: Future[Interpreter], val codePane: CodePane)
    extends InterpreterPane {

    private def checkInterpreter(): Unit = {
      val has = interpreter.isCompleted // .isDefined
      codePane.editor     .enabled = has
      ggProgressInvisible .visible = has
      ggProgress          .visible = !has
      status = if (has) "Ready." else "Initializing..."
    }

    def setInterpreter(in: Interpreter): Unit = {
      codePane.installAutoCompletion(in)
      codePane.editor.requestFocus()
      checkInterpreter()
      if (config.code != "") in.interpret(config.code)
    }

    private val ggStatus: Label = new Label("") {
      peer.putClientProperty("JComponent.sizeVariant", "small")
    }

    private val ggProgress: ProgressBar = new ProgressBar() {
      preferredSize = {
        val d = preferredSize
        d.width = math.min(32, d.width)
        d
      }

      maximumSize = {
        val d = maximumSize
        d.width = math.min(32, d.width)
        d
      }

      peer.putClientProperty("JProgressBar.style", "circular")
      indeterminate = true
    }

    private val ggProgressInvisible: Component = new Component {
      minimumSize   = ggProgress.minimumSize
      preferredSize = ggProgress.preferredSize
      maximumSize   = ggProgress.maximumSize
    }

    private val progressPane: Panel = new OverlayPanel {
      contents += ggProgress
      contents += ggProgressInvisible
    }

    private val statusPane: Panel = new BoxPanel(Orientation.Horizontal) {
      contents += HStrut(4)
      contents += progressPane
      contents += HStrut(4)
      contents += ggStatus
    }

    val component: Panel = new BorderPanel {
      add(codePane.component, BorderPanel.Position.Center)
      add(statusPane        , BorderPanel.Position.South )
    }

    def status: String = {
      val res = ggStatus.text
      if (res == null) "" else res
    }

    def status_=(value: String): Unit = ggStatus.text = value

    def clearStatus(): Unit = status = ""

    def setStatus(result: Interpreter.Result): Unit =
      status = result match {
        case Interpreter.Success(name, _ /* value */) =>
          // println(s"VALUE = $value")
          s"Ok. <$name>"
        case Interpreter.Error(message) =>
          s"! Error : $message"
        case Interpreter.Incomplete =>
          "! Code incomplete"
      }

    def interpret(code: String): Option[Result] = interpreter.value.flatMap(_.toOption).flatMap { in =>
      clearStatus()
      val res = bang(codePane, in)
      res.foreach(setStatus)
      res
    }

    def installExecutionAction(): Unit = {
      val ed    = codePane.editor
      val iMap  = ed.peer.getInputMap(JComponent.WHEN_FOCUSED)
      val aMap  = ed.peer.getActionMap
      iMap.put(config.executeKey, "de.sciss.exec")
      aMap.put("de.sciss.exec", new AbstractAction {
        def actionPerformed(e: ActionEvent): Unit = codePane.activeRange.foreach { range =>
          val text    = codePane.getTextSlice(range)
          val ok      = interpreter.value.exists(_.isSuccess)
          if (ok) {
            codePane.flash(range)
            val success = interpret(text).exists {
              case Interpreter.Success(_,_) => true
              case _ => false
            }
            if (!success) codePane.abortFlash()
          }
        }
      })
    }

    checkInterpreter()
    installExecutionAction()
  }
}
