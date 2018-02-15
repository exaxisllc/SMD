lazy val commonSettings = Seq(
  organization := "org.exaxis.smd",
  version := "1.0.0-SNAPSHOT",
  scalaVersion := "2.12.3"
)

lazy val smdcore = (project in file("smd-core"))
  .settings(commonSettings
    , name := "smd-core"
    , libraryDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo" % "0.12.7"
      , "org.reactivemongo" %% "reactivemongo-akkastream" % "0.12.7"
      , "org.reactivemongo" %% "reactivemongo-iteratees" % "0.12.7"
      , "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"
      , "joda-time" % "joda-time" % "2.9.9"

    )
  )

lazy val smdplay = (project in file("smd-play"))
  .dependsOn(smdcore)
  .settings(commonSettings
    , name := "smd-play"
    , libraryDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo" % "0.12.7"
      , "org.reactivemongo" %% "play2-reactivemongo" % "0.12.7-play26"
      , "org.reactivemongo" %% "reactivemongo-play-json" % "0.12.7-play26"
      , "com.typesafe.play" %% "play" % "2.6.11"
    )
  )


