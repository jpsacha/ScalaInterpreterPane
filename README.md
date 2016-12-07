# ScalaInterpreterPane

[![Flattr this](http://api.flattr.com/button/flattr-badge-large.png)](https://flattr.com/submit/auto?user_id=sciss&url=https%3A%2F%2Fgithub.com%2FSciss%2FScalaInterpreterPane&title=ScalaInterpreterPane&language=Scala&tags=github&category=software)
[![Build Status](https://travis-ci.org/Sciss/ScalaInterpreterPane.svg?branch=master)](https://travis-ci.org/Sciss/ScalaInterpreterPane)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.sciss/scalainterpreterpane_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.sciss/scalainterpreterpane_2.11)

## statement

ScalaInterpreterPane is a Swing component for editing code in the Scala programming language and executing it in an interpreter. The editor component is based on [JSyntaxPane](https://github.com/Sciss/JSyntaxPane). ScalaInterpreterPane is (C)opyright 2010-2016 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU Lesser General Public License](http://github.com/Sciss/ScalaInterpreterPane/blob/master/licenses/ScalaInterpreterPane-License.txt) v2.1+ and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`.

## requirements

ScalaInterpreterPane currently compiles against Scala 2.11, 2.10 and is build with sbt 0.13.

To use it in your own project, add the following to `build.sbt`:

    libraryDependencies += "de.sciss" %% "scalainterpreterpane" % v

The current version `v` is `"1.7.4"`

There is also the `assembly` target which creates a standalone jar in `targets`.

## contributing

Please see the file [CONTRIBUTING.md](CONTRIBUTING.md)

## overview

There are two independent components, `CodePane` and `Interpreter`. The former encapsulates the Swing widget for editing source code with syntax highlighting. The latter encapsulates the Scala REPL.

All have their dedicated configurations, based on a division between (mutable) `ConfigBuilder` and (immutable) `Config`. A builder is implicitly converted to a `Config` when necessary.

For example, to create a plain code view:

```scala

    import de.sciss.scalainterpreter._

    val codeCfg     = CodePane.Config()         // creates a new configuration _builder_
    codeCfg.style   = Style.Light               // use a light color scheme
    codeCfg.text    = """List(1, 2, 3)"""       // initial text to show in the widget
    codeCfg.font    = Seq("Helvetica" -> 16)    // list of preferred fonts
    // add a custom key action
    codeCfg.keyMap += javax.swing.KeyStroke.getKeyStroke("control U") -> { () =>
      println("Action!")
    }

    val codePane    = CodePane(codeCfg)         // create the pane
    val f           = new javax.swing.JFrame("REPL Test")
    // the actual javax.swing.JComponent is found at codePane.component.
    f.getContentPane.add(codePane.component, "Center")
    f.pack()
    f.setVisible(true)
```

To create an interpreter:

```scala

    import de.sciss.scalainterpreter._
    val intpCfg = Interpreter.Config()
    intpCfg.imports :+= "javax.swing._"             // add default imports
    intpCfg.bindings :+= NamedParam("pi", math.Pi)  // add predefined bindings

    val intp = Interpreter(intpCfg)             // create the interpreter
    // invoke the interpreter
    val res = intp.interpret("""Seq.tabulate(10)(i => math.sin(pi * i/10))""")
```

The result of `interpret` is either of `Interpreter.Result`, e.g. `Interpreter.Success` carrying the name and value of the result.

`CodePane` and `Interpreter` can be manually patched together using an entry in the code pane's `keyMap` setting which queries the currently selected text using `codePane.getSelectedText` and feeding that into the `interpret` method. But it is more convenient to use `InterpreterPane` instead, which already combines the two. Furthermore, there is a utility widget `LogPane` which can be used to direct the console output to. Using `SplitPane`, a fully self-contained system can be set up, containing a code pane in the top, a status bar in the middle, and a console log in the bottom:

```scala

    import de.sciss.scalainterpreter._
    val split = SplitPane()
    val f     = new javax.swing.JFrame("REPL Test")
    f.getContentPane.add(split.component, "Center")
    f.pack()
    f.setVisible(true)
```

If you use scala-swing, the components can be wrapped using `swing.Component.wrap(_)`.

## limitations

- auto-completion is based on the standard Scala REPL's auto completion, thus will only see values which have been
  executed in the interpreter before
- auto-completion does not handle wildcard imports executed in the interpreter. completion for wildcard imports defined
  in the configuration should work as excepted
- the result value of an evaluation is currently not directly available

## related

For a standalone desktop application, [ScalaCollider-Swing](https://github.com/Sciss/ScalaColliderSwing) is very 
useful. It has a multi-document-adapter and  window docking and comes with cross-platform and Debian-based binary 
installers.

There are various projects which provide similar functionality or part of the functionality:

- https://github.com/Bridgewater/scala-notebook - console in a browser interface
- https://github.com/jedesah/scala-codesheet-api - code sheet is a slightly different concept with respect to execution
- https://github.com/MasseGuillaume/ScalaKata2 - console in a browser interface
- https://github.com/bobbylight/RSyntaxTextArea - an alternative to JSyntaxPane
- http://code.google.com/p/scalide/ - Swing based REPL, stopped in 2009
- https://github.com/kjellwinblad/ScalaEdit - Swing based editor based on RSyntaxTextArea, stopped in 2011
- https://github.com/Centaur/scalaconsole - Swing based console, stopped in 2011
