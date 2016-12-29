lazy val projectVersion = "1.7.5"
lazy val mimaVersion    = "1.7.0"
lazy val baseName       = "ScalaInterpreterPane"
lazy val baseNameL      = baseName.toLowerCase

name                := baseName
version             := projectVersion
organization        := "de.sciss"
scalaVersion        := "2.12.1"
crossScalaVersions  := Seq("2.12.1", "2.11.8", "2.10.6")
description         := "A Swing based front-end for the Scala REPL (interpreter)"
homepage            := Some(url("https://github.com/Sciss/" + name.value))
licenses            := Seq("LGPL v2.1+" -> url( "http://www.gnu.org/licenses/lgpl-2.1.txt"))

lazy val syntaxPaneVersion  = "1.1.5"
lazy val swingPlusVersion   = "0.2.2"
lazy val jLineVersionOLD    = "2.12.1"
lazy val jLineVersion       = "2.14.1"
lazy val subminVersion      = "0.2.1"

libraryDependencies ++= {
  val sv    = scalaVersion.value
  val jLine = if (sv.startsWith("2.10")) {
    ("org.scala-lang" % "jline" % sv).exclude("org.fusesource.jansi", "jansi") // duplicate stuff in jansi!
  } else if (sv.startsWith("2.11")) {
    "jline" % "jline" % jLineVersionOLD
  } else {
    "jline" % "jline" % jLineVersion
  }
  jLine :: List(
    "de.sciss"       %  "syntaxpane"     % syntaxPaneVersion,
    "de.sciss"       %% "swingplus"      % swingPlusVersion,
    "org.scala-lang" %  "scala-compiler" % sv,
    "de.sciss"       %  "submin"         % subminVersion % "test"
  )
}

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint")

mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion)

fork in run := true

// ---- build info ----

enablePlugins(BuildInfoPlugin)

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

test            in assembly := ()
target          in assembly := baseDirectory.value
assemblyJarName in assembly := s"${name.value}.jar"
