lazy val projectVersion = "1.9.1-SNAPSHOT"
lazy val mimaVersion    = "1.9.0"
lazy val baseName       = "ScalaInterpreterPane"
lazy val baseNameL      = baseName.toLowerCase

lazy val deps = new {
  val main = new {
    val syntaxPane  = "1.1.10"
    val swingPlus   = "0.3.1"
    val jLine       = "2.14.6"
  }
  val test = new {
    val submin      = "0.2.2"
  }
}

lazy val root = project.withId(baseNameL).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name                := baseName,
    version             := projectVersion,
    organization        := "de.sciss",
    scalaVersion        := "2.12.7",
    crossScalaVersions  := Seq("2.12.7", "2.11.12"),
    description         := "A Swing based front-end for the Scala REPL (interpreter)",
    homepage            := Some(url(s"https://git.iem.at/sciss/$baseName")),
    licenses            := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")),
    libraryDependencies ++= Seq(
      "jline"          % "jline"           % deps.main.jLine,
      "de.sciss"       %  "syntaxpane"     % deps.main.syntaxPane,
      "de.sciss"       %% "swingplus"      % deps.main.swingPlus,
      "org.scala-lang" %  "scala-compiler" % scalaVersion.value,
      "de.sciss"       %  "submin"         % deps.test.submin % Test
    ),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint", "-Xsource:2.13"),
    mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion),
    fork in run := true,
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt) => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq( (lic, _) )) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.scalainterpreter"
  )
  .settings(publishSettings)
  .settings(assemblySettings)

// ---- publishing ----
lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := { val n = name.value
<scm>
  <url>git@git.iem.at:sciss/{n}.git</url>
  <connection>scm:git:git@git.iem.at:sciss/{n}.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>
  }
)

// ---- standalone ----
lazy val assemblySettings = Seq(
  test            in assembly := {},
  target          in assembly := baseDirectory.value,
  assemblyJarName in assembly := s"${name.value}.jar"
)
