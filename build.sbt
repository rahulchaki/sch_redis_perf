ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

enablePlugins(JavaServerAppPackaging, GatlingPlugin, JmhPlugin)

lazy val root = (project in file("."))
  .settings(
    name := "sch_redis_perf",
    Compile / run / mainClass := Some("com.streamsets.Main"),
    libraryDependencies ++= Seq(
      "com.github.pureconfig" %% "pureconfig" % "0.17.2",
      "org.redisson" % "redisson" % "3.20.0",
      "com.google.code.gson" % "gson" % "2.10.1",
      "commons-codec" % "commons-codec" % "1.15",
      "org.springframework.boot" % "spring-boot-starter-webflux" % "3.0.4",
      "org.openjdk.jmh" % "jmh-generator-annprocess" % "1.36",
      "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.9.1" % "test",
      "io.gatling" % "gatling-test-framework" % "3.9.1" % "test"
    )
  )
