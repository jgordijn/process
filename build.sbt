lazy val root = (project in file(".")).
  settings(
    organization := "com.github.jgordijn",
    name := "process",
    version := "0.1.5",
    scalaVersion := "2.11.6",
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    }
      , pomIncludeRepository := { _ => false }
      , pomExtra := (
      <url>https://github.com/jgordijn/process</url>
            <licenses>
          <license>
          <name>Apache 2.0</name>
          <url>http://opensource.org/licenses/Apache-2.0</url>
            <distribution>repo</distribution>
          </license>
          </licenses>
          <scm>
        <url>git@github.com:jgordijn/process.git</url>
        <connection>scm:git:git@github.com:jgordijn/process.git</connection>
          </scm>
          <developers>
          <developer>
          <id>jgordijn</id>
          <name>Jeroen Gordijn</name>
            </developer>
          </developers>)
  )

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.9",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.9",
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
)
