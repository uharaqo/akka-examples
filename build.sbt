organization := "com.github.uharaqo"
name := "akka-examples"
scalaVersion := "2.13.8"
ThisBuild / scalaVersion := "2.13.8"

val AkkaVersion                = "2.6.19"
val AkkaHttpVersion            = "10.2.9"
val AkkaManagementVersion      = "1.1.3"
val AkkaPersistenceJdbcVersion = "5.0.4"
val AlpakkaKafkaVersion        = "3.0.0"
val AkkaProjectionVersion      = "1.2.4"
val ScalikeJdbcVersion         = "4.0.0"

lazy val root = (project in file(".")).aggregate(cart, cartAnalytics, cartOrder)

//lazy val actors = (project in file("1_akka_actors"))
//  .settings(
//    libraryDependencies ++= Seq(
//      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
//      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
//      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
//      "ch.qos.logback" % "logback-classic" % "1.2.11"
//    )
//  )

val baseSettings = Seq(
  organization := "com.github.uharaqo",
  scalaVersion := "2.13.8",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-slf4j"       % AkkaVersion,
    // test
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
    "org.scalatest"     %% "scalatest"                % "3.2.12"    % Test
  ),
//  Compile / scalacOptions ++= Seq(
//    "-target:11"
//    "-deprecation",
//    "-feature",
//    "-unchecked",
//    "-Xlog-reflective-calls",
//    "-Xlint"
//  ),
//  Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
  Test / parallelExecution := false,
//  Test / testOptions += Tests.Argument("-oDF"),
//  Test / logBuffered := false
  run / fork := false,
  Global / cancelable := false // ctrl-c
)

lazy val cart = (project in file("2_1_cart"))
  .settings(baseSettings)
  .settings(
    name := "shopping-cart-service",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream"     % AkkaVersion,
      "com.typesafe"      %% "ssl-config-core" % "0.6.1", // akka-stream uses an old version that conflicts with other dependencies
      "ch.qos.logback"     % "logback-classic" % "1.2.11",
      // http
      "com.typesafe.akka" %% "akka-http"            % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      // grpc
      "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion,
      // persistence
      "org.postgresql"      % "postgresql"                 % "42.3.4",
      "com.typesafe.akka"  %% "akka-serialization-jackson" % AkkaVersion,
      "com.typesafe.akka"  %% "akka-persistence-typed"     % AkkaVersion,
      "com.lightbend.akka" %% "akka-persistence-jdbc"      % AkkaPersistenceJdbcVersion,
      // cluster
      "com.typesafe.akka"             %% "akka-cluster-typed"                % AkkaVersion,
      "com.typesafe.akka"             %% "akka-cluster-sharding-typed"       % AkkaVersion,
      "com.typesafe.akka"             %% "akka-discovery"                    % AkkaVersion,
      "com.lightbend.akka.management" %% "akka-management"                   % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http"      % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
      "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % AkkaManagementVersion,
      // projection
      "com.typesafe.akka"  %% "akka-persistence-query"       % AkkaVersion,
      "com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion,
      "com.lightbend.akka" %% "akka-projection-jdbc"         % AkkaProjectionVersion,
      "org.scalikejdbc"    %% "scalikejdbc"                  % ScalikeJdbcVersion,
      "org.scalikejdbc"    %% "scalikejdbc-config"           % ScalikeJdbcVersion,
      // projection publish
      "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
      // test
      "org.scalatest"      %% "scalatest"                % "3.2.12"              % Test,
      "com.typesafe.akka"  %% "akka-stream-testkit"      % AkkaVersion           % Test,
      "com.typesafe.akka"  %% "akka-actor-testkit-typed" % AkkaVersion           % Test,
      "com.typesafe.akka"  %% "akka-stream-testkit"      % AkkaVersion           % Test,
      "com.typesafe.akka"  %% "akka-persistence-testkit" % AkkaVersion           % Test,
      "com.lightbend.akka" %% "akka-projection-testkit"  % AkkaProjectionVersion % Test,
    ),
    inConfig(Compile)(
      Seq(PB.protoSources += baseDirectory.value / "src/main/protobuf")
    )
  )
  .enablePlugins(AkkaGrpcPlugin)
//  .enablePlugins(JavaAppPackaging)

val cartAnalytics = (project in file("2_2_cart_analytics"))
  .settings(
    name := "shopping-cart-analytics"
  )
  .dependsOn(cart)

val cartOrder = (project in file("2_3_cart_order"))
  .settings(
    name := "shopping-cart-order"
  )
  .dependsOn(cart)

//enablePlugins(JavaAppPackaging, DockerPlugin)
//dockerBaseImage := "docker.io/library/adoptopenjdk:11-jre-hotspot"
//dockerUsername := sys.props.get("docker.username")
//dockerRepository := sys.props.get("docker.registry")
//ThisBuild / dynverSeparator := "-"
