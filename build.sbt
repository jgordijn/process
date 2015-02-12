lazy val root = (project in file(".")).
  settings(
    name := "process",
    version := "1.0",
    scalaVersion := "2.11.5"
  )

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.9"

)
