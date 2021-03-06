import sbt.Credentials
import sbt.Keys.{credentials, homepage, pomExtra, publishMavenStyle, publishTo}

lazy val commonSettings = Seq(
  organization := "org.exaxis.smd",
  version := "1.0.3-SNAPSHOT",
  scalaVersion := "2.12.4",
  homepage := Some(url("https://github.com/exaxisllc/SMD")),
  pomExtra :=
    <scm>
      <connection>
        scm:git:git://github.com/exaxisllc/SMD.git
      </connection>
      <url>
        https://github.com/exaxisllc/SMD
      </url>
    </scm>
    <developers>
      <developer>
        <id>berryware</id>
        <name>David Berry</name>
      </developer>
    </developers>,

  credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),
  publishMavenStyle := true,
  libraryDependencies ++= Seq(
    "com.typesafe.play" % "play-json-joda_2.12" % "2.6.0" % Test
    , "org.scaldi" %% "scaldi" % "0.5.8" % Test
    , "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "2.1.1" % Test  // force a newer version
    , "com.github.simplyscala" %% "scalatest-embedmongo" % "0.2.4" %  Test
    , "org.scalatest" %% "scalatest" % "3.0.5" % Test),
  packagedArtifacts in publish ~= { m =>
    val classifiersToExclude = Set(
      Artifact.SourceClassifier,
      Artifact.DocClassifier
    )
    m.filter { case (art, _) =>
      art.classifier.forall(c => !classifiersToExclude.contains(c))
    }
  }
)

lazy val root = (project in file("."))
  .aggregate(smdcore, smdplay)
  .settings( name := "smd"
    , publishTo := None )

lazy val smdcore = (project in file("smd-core"))
  .settings(commonSettings
    , name := "smd-core"
    , libraryDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo" % "0.15.1"
      , "org.reactivemongo" %% "reactivemongo-akkastream" % "0.15.1"
      , "org.reactivemongo" %% "reactivemongo-iteratees" % "0.15.1"
      , "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
      , "joda-time" % "joda-time" % "2.9.9"
    )
    , publishTo := Some("bintray" at "https://api.bintray.com/maven/exaxisllc/SMD/smd-core;publish=1")
  )

lazy val smdplay = (project in file("smd-play"))
  .dependsOn(smdcore)
  .settings(commonSettings
    , name := "smd-play"
    , libraryDependencies ++= Seq(
      "org.reactivemongo" %% "play2-reactivemongo" % "0.15.1-play26"
      , "org.reactivemongo" %% "reactivemongo-play-json" % "0.15.1-play26"
      , "com.typesafe.play" %% "play" % "2.6.18" % "provided"
      , "com.typesafe.play" %% "play-ws" % "2.6.18"  % "provided"
    )
    , publishTo := Some("bintray" at "https://api.bintray.com/maven/exaxisllc/SMD/smd-play;publish=1")
  )
