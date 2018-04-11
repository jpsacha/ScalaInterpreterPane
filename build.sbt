lazy val projectVersion = "1.9.0"
lazy val mimaVersion    = "1.9.0"
lazy val baseName       = "ScalaInterpreterPane"
lazy val baseNameL      = baseName.toLowerCase

lazy val syntaxPaneVersion  = "1.1.9"
lazy val swingPlusVersion   = "0.3.0"
lazy val jLineVersion       = "2.14.6"
lazy val subminVersion      = "0.2.2"

lazy val root = project.withId(baseNameL).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name                := baseName,
    version             := projectVersion,
    organization        := "de.sciss",
    scalaVersion        := "2.12.5",
    crossScalaVersions  := Seq("2.12.5", "2.11.12"),
    description         := "A Swing based front-end for the Scala REPL (interpreter)",
    homepage            := Some(url(s"https://github.com/Sciss/$baseName")),
    licenses            := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")),
    libraryDependencies ++= Seq(
      "jline"          % "jline"           % jLineVersion,
      "de.sciss"       %  "syntaxpane"     % syntaxPaneVersion,
      "de.sciss"       %% "swingplus"      % swingPlusVersion,
      "org.scala-lang" %  "scala-compiler" % scalaVersion.value,
      "de.sciss"       %  "submin"         % subminVersion % "test"
    ),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint"),
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
)

// ---- standalone ----
lazy val assemblySettings = Seq(
  test            in assembly := {},
  target          in assembly := baseDirectory.value,
  assemblyJarName in assembly := s"${name.value}.jar"
)
