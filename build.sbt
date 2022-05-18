organization := "com.github.uharaqo"
name := "akka-examples"
scalaVersion := "2.13.8"

val AkkaVersion = "2.6.19"
//lazy val discoveryVersion = "1.0.9"
//lazy val akkaHttpVersion = "10.2.3"
//
lazy val root = (project in file("."))
  .aggregate(actors)

lazy val actors = (project in file("1_akka_actors"))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.2.11"
    )
  )
