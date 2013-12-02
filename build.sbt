import AssemblyKeys._

name         := "ScalaInterpreterPane"

version      := "1.5.0-SNAPSHOT"

organization := "de.sciss"

scalaVersion := "2.10.3"

description  := "A Swing based front-end for the Scala REPL (interpreter)"

homepage     := Some(url("https://github.com/Sciss/" + name.value))

licenses     := Seq("LGPL v2.1+" -> url( "http://www.gnu.org/licenses/lgpl-2.1.txt"))

libraryDependencies ++= Seq(
  "de.sciss" % "jsyntaxpane" % "1.0.+",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value
)

retrieveManaged := true

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")

scalacOptions ++= { val sv = scalaVersion.value
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

seq(appbundle.settings: _*)

appbundle.icon   := Some(file("application.icns"))

appbundle.target := baseDirectory.value

// ---- ls.implicit.ly ----

seq(lsSettings :_*)

(LsKeys.tags   in LsKeys.lsync) := Seq("repl", "interpreter")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) := Some(name.value)

