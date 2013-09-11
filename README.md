# ScalaInterpreterPane

## statement

ScalaInterpreterPane is a Swing component for editing code in the Scala programming language and executing it in an interpreter. The editor component is based on [JSyntaxPane](https://github.com/Sciss/JSyntaxPane). ScalaInterpreterPane is (C)opyright 2010-2013 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU Lesser General Public License](http://github.com/Sciss/ScalaInterpreterPane/blob/master/licenses/ScalaInterpreterPane-License.txt) and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`.

## requirements

ScalaInterpreterPane currently compiles against Scala 2.10 (default) and 2.9.3 and is build with sbt 0.13.

To use it in your own project, add the following to `build.sbt`:

    libraryDependencies += "de.sciss" %% "scalainterpreterpane" % v

The current version `v` is `1.4.1+`.

There is also an `appbundle` target for sbt which creates a standalone OS X application, and the `assembly` target which creates a standalone jar in `targets`.

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

