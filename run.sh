#!/bin/sh
java -cp target/scala_2.8.0/scalainterpreterpane_2.8.0-0.16.jar:lib_managed/scala_2.8.0/compile/jsyntaxpane-0.9.5-b29.jar:${SCALA_HOME}/lib/scala-library.jar:${SCALA_HOME}/lib/scala-compiler.jar de.sciss.scalainterpreter.Main
