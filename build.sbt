import AssemblyKeys._

name                := "ScalaInterpreterPane"

version             := "1.6.2"

organization        := "de.sciss"

scalaVersion        := "2.11.0"

crossScalaVersions  := Seq("2.11.0", "2.10.4")

description         := "A Swing based front-end for the Scala REPL (interpreter)"

homepage            := Some(url("https://github.com/Sciss/" + name.value))

licenses            := Seq("LGPL v2.1+" -> url( "http://www.gnu.org/licenses/lgpl-2.1.txt"))

def syntaxPaneVersion = "1.1.2"

libraryDependencies ++= {
  val sv    = scalaVersion.value
  val jline = if (sv startsWith "2.11") {
    val a = "jline" % "jline" % "2.11"  // note: jline version is pure coincidence
    a :: Nil
  } else {
    val a = ("org.scala-lang" % "jline" % sv).exclude("org.fusesource.jansi", "jansi") // duplicate stuff in jansi!
    a :: Nil
  }
  Seq(
    "de.sciss"       % "syntaxpane"     % syntaxPaneVersion,
    "org.scala-lang" % "scala-compiler" % sv
  ) ++ jline
}

// retrieveManaged := true

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xfuture")

fork in run := true

// ---- build info ----

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
  BuildInfoKey.map(homepage) { case (k, opt) => k -> opt.get },
  BuildInfoKey.map(licenses) { case (_, Seq( (lic, _) )) => "license" -> lic }
)

buildInfoPackage := "de.sciss.scalainterpreter"

// ---- publishing ----

publishMavenStyle := true

publishTo :=
  Some(if (version.value endsWith "-SNAPSHOT")
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := { val n = name.value
<scm>
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>
}

// ---- standalone ----

seq(assemblySettings: _*)

test in assembly := ()

target in assembly := baseDirectory.value

jarName in assembly := s"${name.value}.jar"

seq(appbundle.settings: _*)

appbundle.icon   := Some(file("icons") / "application.icns")

appbundle.target := baseDirectory.value

// ---- ls.implicit.ly ----

seq(lsSettings :_*)

(LsKeys.tags   in LsKeys.lsync) := Seq("repl", "interpreter")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) := Some(name.value)

