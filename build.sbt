lazy val projectVersion = "1.11.1"
lazy val mimaVersion    = "1.11.0"
lazy val baseName       = "ScalaInterpreterPane"
lazy val baseNameL      = baseName.toLowerCase

lazy val deps = new {
  val main = new {
    val syntaxPane  = "1.2.0"
    val swingPlus   = "0.5.0"
    val jLine       = "2.14.6"
  }
  val test = new {
    val submin      = "0.3.5"
  }
}

// sonatype plugin requires that these are in global
ThisBuild / version      := projectVersion
ThisBuild / organization := "de.sciss"

lazy val root = project.withId(baseNameL).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name                := baseName,
    scalaVersion        := "2.13.7",
    crossScalaVersions  := Seq("3.1.0", "2.13.7", "2.12.15"),
    description         := "A Swing based front-end for the Scala REPL (interpreter)",
    homepage            := Some(url(s"https://github.com/Sciss/$baseName")),
    licenses            := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")),
    libraryDependencies ++= Seq(
      "jline"          %  "jline"          % deps.main.jLine,
      "de.sciss"       %  "syntaxpane"     % deps.main.syntaxPane,
      "de.sciss"       %% "swingplus"      % deps.main.swingPlus,
      "de.sciss"       %  "submin"         % deps.test.submin % Test
    ),
    libraryDependencies += {
      val isDot = scalaVersion.value.startsWith("3.")
      if (isDot) 
        "org.scala-lang" %% "scala3-compiler" % scalaVersion.value
      else 
        "org.scala-lang" %  "scala-compiler"  % scalaVersion.value
    },
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xlint", "-Xsource:2.13"),
    Compile / unmanagedSourceDirectories += {
      val sourceDir = (Compile / sourceDirectory).value
      val sv  = CrossVersion.partialVersion(scalaVersion.value)
      val sub = sv match {
        case Some((3, _)) => "scala-2.14+"
        case _            => "scala-2.14-"
      }
      sourceDir / sub
    },
    mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion),
    run / fork := true,
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
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
  developers := List(
    Developer(
      id    = "sciss",
      name  = "Hanns Holger Rutz",
      email = "contact@sciss.de",
      url   = url("https://www.sciss.de")
    )
  ),
  scmInfo := {
    val h = "github.com"
    val a = s"Sciss/${name.value}"
    Some(ScmInfo(url(s"https://$h/$a"), s"scm:git@$h:$a.git"))
  },
)

// ---- standalone ----
lazy val assemblySettings = Seq(
  assembly / test            := {},
  assembly / target          := baseDirectory.value,
  assembly / assemblyJarName := s"${name.value}.jar"
)
