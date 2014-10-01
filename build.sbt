import AssemblyKeys._

name                := "ScalaInterpreterPane"

version             := "1.7.0"

organization        := "de.sciss"

scalaVersion        := "2.11.2"

crossScalaVersions  := Seq("2.11.2", "2.10.4")

description         := "A Swing based front-end for the Scala REPL (interpreter)"

homepage            := Some(url("https://github.com/Sciss/" + name.value))

licenses            := Seq("LGPL v2.1+" -> url( "http://www.gnu.org/licenses/lgpl-2.1.txt"))

lazy val syntaxPaneVersion  = "1.1.4"

lazy val swingPlusVersion   = "0.2.0"

lazy val jLineVersion       = "2.12"

libraryDependencies ++= {
  val sv    = scalaVersion.value
  val jLine = if (sv startsWith "2.11")
    "jline" % "jline" % jLineVersion
  else
    ("org.scala-lang" % "jline" % sv).exclude("org.fusesource.jansi", "jansi") // duplicate stuff in jansi!
  //
  //  val swing = if (sv startsWith "2.11")
  //    "org.scala-lang.modules" %% "scala-swing" % "1.0.1"
  //  else
  //    "org.scala-lang" % "scala-swing" % sv
  //
  jLine :: List(
    "de.sciss"       %  "syntaxpane"     % syntaxPaneVersion,
    "de.sciss"       %% "swingplus"      % swingPlusVersion,
    "org.scala-lang" %  "scala-compiler" % sv
  )
}

// retrieveManaged := true

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture")

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
  Some(if (isSnapshot.value)
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

