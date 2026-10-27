name := "Clustered Smart Home Alarm System"
version := "0.1.0"

val scala3Version = "3.9.0"
val scalaTestVersion = "3.2.20"
val PekkoVersion = "1.7.1"
val scalaLoggingVersion = "3.9.6"
val logbackVersion = "1.6.5"

ThisBuild / scalaVersion := scala3Version
ThisBuild / libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion,
  "org.apache.pekko" %% "pekko-actor-testkit-typed" % PekkoVersion % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion
)

lazy val root = project
  .in(file("."))
  .settings(
    //name := "cluster",
    run / fork := true,
    assembly / assemblyOutputPath := baseDirectory.value / "target" / "cluster.jar",
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-cluster-typed" % PekkoVersion,
      "org.apache.pekko" %% "pekko-cluster-sharding-typed" % PekkoVersion,
      "org.apache.pekko" %% "pekko-serialization-jackson" % PekkoVersion
    ),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
      case PathList("module-info.class")                              => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )
