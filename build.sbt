import AssemblyKeys._

name := "ScalaInterpreterPane"

version := "1.4.1-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.10.2"

crossScalaVersions in ThisBuild := Seq("2.10.2", "2.9.3")

description := "A Swing based front-end for the Scala REPL (interpreter)"

homepage := Some(url("https://github.com/Sciss/ScalaInterpreterPane"))

licenses := Seq("LGPL v2.1+" -> url( "http://www.gnu.org/licenses/lgpl-2.1.txt"))

libraryDependencies += "de.sciss" % "jsyntaxpane" % "1.0.+"

libraryDependencies <+= scalaVersion { sv =>
  "org.scala-lang" % "scala-compiler" % sv
}

retrieveManaged := true

scalacOptions ++= Seq("-deprecation", "-unchecked")

scalacOptions <++= scalaVersion.map { sv =>
  def versionSeq(a: String) = a split '.' map (_.toInt)
  def compareVersion(a: String, b: String) = {
    versionSeq(a) zip versionSeq(b) map { case (i, j) => i compare j } find (_ != 0) getOrElse 0
  }
  def versionGeq(a: String, b: String) = compareVersion(a, b) >= 0
  if (versionGeq(sv, "2.10.0"))
    "-feature" :: "-language:implicitConversions" :: Nil
  else
    Nil
}

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

publishTo <<= version { (v: String) =>
  Some( if( v.endsWith( "-SNAPSHOT" ))
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra <<= name { n =>
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

test in assembly := {}

seq(appbundle.settings: _*)

appbundle.icon := Some(file("application.icns"))

appbundle.target <<= baseDirectory

// ---- ls.implicit.ly ----

seq(lsSettings :_*)

(LsKeys.tags in LsKeys.lsync) := Seq("repl", "interpreter")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) <<= name(Option.apply)

// bug in ls -- doesn't find the licenses from global scope
(licenses in LsKeys.lsync) := Seq("LGPL v2.1+" -> url( "http://www.gnu.org/licenses/lgpl-2.1.txt"))
