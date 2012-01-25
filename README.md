## ScalaInterpreterPane

### statement

ScalaInterpreterPane is a Swing component for editing code in the Scala programming language and executing it in an interpreter. The editor component is based on [JSyntaxPane](http://code.google.com/p/jsyntaxpane/). ScalaInterpreterPane is (C)opyright 2010-2012 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU Lesser General Public License](http://github.com/Sciss/ScalaInterpreterPane/blob/master/licenses/ScalaInterpreterPane-License.txt) and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`.

### requirements

ScalaInterpreterPane currently compiles against Scala 2.9.1 and is build with xsbt (sbt 0.11).

To use it in your own project, add the following to `build.sbt`:

    resolvers += "Clojars Repository" at "http://clojars.org/repo"

    libraryDependencies += "de.sciss" %% "scalainterpreterpane" % "0.18"

There is also an `appbundle` target for sbt which creates a standalone OS X application, and the `assembly` target which creates a standalone jar in `targets`.

### creating an IntelliJ IDEA project

If you haven't globally installed the sbt-idea plugin yet, create the following contents in `~/.sbt/plugins/build.sbt`:

    resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"
    
    addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")

Then to create the IDEA project, run the following two commands from the xsbt shell:

    > set ideaProjectName := "ScalaInterpreterPane"
    > gen-idea

### download

The current sources can be downloaded from [github.com/Sciss/ScalaInterpreterPane](http://github.com/Sciss/ScalaInterpreterPane).
